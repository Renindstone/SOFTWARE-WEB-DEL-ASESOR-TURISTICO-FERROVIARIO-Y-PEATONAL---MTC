package com.turismo.service;

import com.turismo.dto.InformeConsolidadoDTO;
import com.turismo.dto.VisitanteDTO;
import com.turismo.exception.AforoCompletoException;
import com.turismo.model.CategoriaVisitante;
import com.turismo.model.Estacion;
import com.turismo.model.InformePlanificacion;
import com.turismo.model.InformeVisitante;
import com.turismo.model.PrevisionClima;
import com.turismo.model.RutaPeatonal;
import com.turismo.model.ServicioTren;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.CategoriaVisitanteRepository;
import com.turismo.repository.InformePlanificacionRepository;
import com.turismo.repository.InformeVisitanteRepository;
import com.turismo.util.GeneradorPdf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-08/RF-18 (CN-07/CN-09): generacion del informe consolidado. Cubre que el
 * informe reune ruta, clima y tarifas del grupo, que se persiste con codigo
 * correlativo junto a su detalle de visitantes, y que el aforo agotado corta
 * la emision antes de guardar.
 *
 * TarifaService se usa real (con el repositorio simulado) porque las tarifas
 * por edad son justamente lo que estas pruebas verifican; sustituirlo por un
 * doble haria que el total del informe lo decidiera la propia prueba.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InformeServiceTest {

    private static final LocalDate FECHA_VISITA = LocalDate.of(2026, 9, 2);

    @Mock
    private InformePlanificacionRepository informePlanificacionRepository;
    @Mock
    private InformeVisitanteRepository informeVisitanteRepository;
    @Mock
    private RutaPeatonalService rutaPeatonalService;
    @Mock
    private ClimaService climaService;
    @Mock
    private AforoService aforoService;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private GeneradorPdf generadorPdf;
    @Mock
    private CategoriaVisitanteRepository categoriaVisitanteRepository;

    private InformeService informeService;

    private Estacion origen;
    private ZonaTuristica destino;
    private ServicioTren servicio;

    /** Mismos tramos que siembra 02_datos.sql en categoria_visitante. */
    private static CategoriaVisitante crearCategoria(String ambito, String nombre,
                                                      Short edadMinima, Short edadMaxima, String factor) {
        CategoriaVisitante categoria = new CategoriaVisitante();
        categoria.setAmbito(ambito);
        categoria.setNombre(nombre);
        categoria.setEdadMinima(edadMinima);
        categoria.setEdadMaxima(edadMaxima);
        categoria.setFactorPrecio(new BigDecimal(factor));
        return categoria;
    }

    private List<VisitanteDTO> grupo(int... edades) {
        return java.util.Arrays.stream(edades)
                .mapToObj(edad -> new VisitanteDTO(edad, 1))
                .collect(java.util.stream.Collectors.toList());
    }

    @BeforeEach
    void prepararEscenario() {
        TarifaService tarifaService = new TarifaService(categoriaVisitanteRepository);
        informeService = new InformeService(informePlanificacionRepository, informeVisitanteRepository,
                rutaPeatonalService, climaService, aforoService, tarifaService,
                auditoriaService, generadorPdf);

        // PeruRail cobra adulto desde los 12; la zona mantiene nino hasta los 17.
        when(categoriaVisitanteRepository.findByAmbitoOrderByEdadMinimaAsc(CategoriaVisitante.AMBITO_TREN))
                .thenReturn(List.of(
                        crearCategoria(CategoriaVisitante.AMBITO_TREN, "Infante", (short) 0, (short) 2, "0.0000"),
                        crearCategoria(CategoriaVisitante.AMBITO_TREN, "Nino", (short) 3, (short) 11, "0.5000"),
                        crearCategoria(CategoriaVisitante.AMBITO_TREN, "Adulto", (short) 12, null, "1.0000")));
        when(categoriaVisitanteRepository.findByAmbitoOrderByEdadMinimaAsc(CategoriaVisitante.AMBITO_ZONA))
                .thenReturn(List.of(
                        crearCategoria(CategoriaVisitante.AMBITO_ZONA, "Infante", (short) 0, (short) 2, "0.0000"),
                        crearCategoria(CategoriaVisitante.AMBITO_ZONA, "Nino", (short) 3, (short) 17, "0.6800"),
                        crearCategoria(CategoriaVisitante.AMBITO_ZONA, "Adulto", (short) 18, null, "1.0000")));

        origen = new Estacion();
        origen.setId(4);
        origen.setNombre("Estacion Ollantaytambo");
        origen.setLatitud(new BigDecimal("-13.258600"));
        origen.setLongitud(new BigDecimal("-72.265000"));
        origen.setEstado("Activa");

        destino = new ZonaTuristica();
        destino.setId(6);
        destino.setNombre("Conjunto Arqueologico de Ollantaytambo");
        destino.setLatitud(new BigDecimal("-13.254466"));
        destino.setLongitud(new BigDecimal("-72.268563"));
        destino.setCostoAprox(new BigDecimal("70.00"));

        servicio = new ServicioTren();
        servicio.setId(3);
        servicio.setTarifa(new BigDecimal("145.00"));

        // El calculo real de la ruta vive en RutaPeatonalServiceTest (CB-01/CB-02).
        when(rutaPeatonalService.calcularRutaPeatonalIdaVuelta(any(), any()))
                .thenAnswer(invocacion -> {
                    var dto = new com.turismo.dto.RutaCalculadaDTO();
                    dto.setNombre("Circuito Ollantaytambo - Fortaleza");
                    dto.setDistanciaKm(new BigDecimal("1.20"));
                    dto.setTiempoEstimadoMin(14);
                    dto.setDificultad("Baja");
                    return dto;
                });
        when(rutaPeatonalService.obtenerOCrearRuta(any(), any(), any())).thenReturn(new RutaPeatonal());
        when(informePlanificacionRepository.save(any(InformePlanificacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(informePlanificacionRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        when(aforoService.consultarCupoDisponible(any(), any())).thenReturn(Optional.empty());
    }

    /** CN-07: el informe reune ruta, clima, tarifa y total estimado. */
    @Test
    void generaElInformeConRutaClimaYTarifa() {
        PrevisionClima clima = new PrevisionClima();
        clima.setTemperaturaMinC(new BigDecimal("5.0"));
        clima.setTemperaturaMaxC(new BigDecimal("19.4"));
        clima.setProbabilidadLluvia(new BigDecimal("30.0"));
        clima.setEstadoClima("Parcialmente nublado");
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.of(clima));

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(35), null);

        assertThat(informe.getEstacionOrigen()).isEqualTo("Estacion Ollantaytambo");
        assertThat(informe.getZonaDestino()).isEqualTo("Conjunto Arqueologico de Ollantaytambo");
        assertThat(informe.getRuta().getDistanciaKm()).isEqualByComparingTo("1.20");
        assertThat(informe.getRuta().getDificultad()).isEqualTo("Baja");
        // Las temperaturas del SENAMHI deben llegar al informe, no solo la lluvia.
        assertThat(informe.getTemperaturaMinimaC()).isEqualByComparingTo("5.0");
        assertThat(informe.getTemperaturaMaximaC()).isEqualByComparingTo("19.4");
        assertThat(informe.getEstadoClima()).isEqualTo("Parcialmente nublado");
        assertThat(informe.getTarifaTren()).isEqualByComparingTo("145.00");
        // Un adulto: tarifa completa de tren + costo completo de la zona.
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("215.00");
    }

    /** Sin declarar edades, el informe asume un adulto: el turista que consulta solo. */
    @Test
    void asumeUnAdultoCuandoNoSeDeclaraNingunaEdad() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, null, null);

        assertThat(informe.getTotalPersonas()).isEqualTo(1);
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("215.00");
        verify(aforoService).validarAforoDisponible(destino, FECHA_VISITA, 1);
    }

    /**
     * RF-18: la familia sembrada como INF-0001 (dos adultos y un nino de 8).
     * Tren: 145.00 x2 + 145.00 x 0.50. Zona: 70.00 x2 + 70.00 x 0.68.
     */
    @Test
    void aplicaLaTarifaQueCorrespondeACadaEdadDelGrupo() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(38, 36, 8), null);

        assertThat(informe.getTotalPersonas()).isEqualTo(3);
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("550.10");
        assertThat(informe.getVisitantes())
                .extracting(VisitanteDTO::getEdad, VisitanteDTO::getCategoriaTren, VisitanteDTO::getCategoriaZona)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(38, "Adulto", "Adulto"),
                        org.assertj.core.api.Assertions.tuple(36, "Adulto", "Adulto"),
                        org.assertj.core.api.Assertions.tuple(8, "Nino", "Nino"));
    }

    /**
     * El corte de edad no es el mismo en los dos proveedores: a los 15 anos se
     * paga adulto en el tren y nino en la zona. Un unico tramo compartido
     * cobraria de mas en la entrada o de menos en el boleto.
     */
    @Test
    void cobraAdultoEnElTrenYNinoEnLaZonaAlAdolescente() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(15), null);

        VisitanteDTO adolescente = informe.getVisitantes().get(0);
        assertThat(adolescente.getCategoriaTren()).isEqualTo("Adulto");
        assertThat(adolescente.getCategoriaZona()).isEqualTo("Nino");
        assertThat(adolescente.getSubtotalTren()).isEqualByComparingTo("145.00");
        assertThat(adolescente.getSubtotalZona()).isEqualByComparingTo("47.60");
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("192.60");
    }

    /** Un infante no paga tren ni entrada, pero sigue ocupando cupo de aforo. */
    @Test
    void elInfanteNoPagaPeroCuentaParaElAforo() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(30, 1), null);

        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("215.00");
        verify(aforoService).validarAforoDisponible(destino, FECHA_VISITA, 2);
    }

    /**
     * uq_visitante_informe_edad admite una sola fila por edad: tres personas de
     * la misma edad viajan como una linea con cantidad 3.
     */
    @Test
    void agrupaEnUnaSolaLineaALosVisitantesDeLaMismaEdad() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(30, 30, 30), null);

        assertThat(informe.getVisitantes()).hasSize(1);
        assertThat(informe.getVisitantes().get(0).getCantidad()).isEqualTo(3);
        assertThat(informe.getTotalPersonas()).isEqualTo(3);
        verify(informeVisitanteRepository, times(1)).save(any(InformeVisitante.class));
    }

    /** Sin servicio de tren solo se cobra la entrada a la zona. */
    @Test
    void omiteLaTarifaDeTrenCuandoNoSeEligeServicio() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, null, FECHA_VISITA, grupo(30), null);

        assertThat(informe.getTarifaTren()).isNull();
        assertThat(informe.getVisitantes().get(0).getCategoriaTren()).isNull();
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("70.00");
    }

    /** El codigo sigue el formato INF-0001 del diccionario de datos (6.4). */
    @Test
    void persisteElInformeConCodigoCorrelativo() {
        InformePlanificacion ultimo = new InformePlanificacion();
        ultimo.setCodigo("INF-0007");
        when(informePlanificacionRepository.findTopByOrderByIdDesc()).thenReturn(Optional.of(ultimo));
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(30), null);

        ArgumentCaptor<InformePlanificacion> captor = ArgumentCaptor.forClass(InformePlanificacion.class);
        verify(informePlanificacionRepository).save(captor.capture());

        assertThat(captor.getValue().getCodigo()).isEqualTo("INF-0008");
        assertThat(informe.getCodigo()).isEqualTo("INF-0008");
        // Consulta anonima: InfIdUsuario queda NULL (diccionario 6.4).
        assertThat(captor.getValue().getUsuario()).isNull();
        assertThat(captor.getValue().getFechaVisita()).isEqualTo(FECHA_VISITA);
    }

    /** El detalle del grupo se guarda colgado del informe recien creado. */
    @Test
    void persisteElDetalleDelGrupoJuntoAlInforme() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(38, 8), null);

        ArgumentCaptor<InformeVisitante> captor = ArgumentCaptor.forClass(InformeVisitante.class);
        verify(informeVisitanteRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(fila -> fila.getEdad().intValue())
                .containsExactly(38, 8);
        assertThat(captor.getAllValues())
                .allMatch(fila -> fila.getInforme() != null && "INF-0001".equals(fila.getInforme().getCodigo()));
        assertThat(captor.getAllValues().get(1).getCategoriaTren().getNombre()).isEqualTo("Nino");
        assertThat(captor.getAllValues().get(1).getCategoriaZona().getNombre()).isEqualTo("Nino");
    }

    /** CN-09: con el aforo agotado no se emite ni se persiste informe alguno. */
    @Test
    void noPersisteElInformeCuandoElAforoEstaCompleto() {
        doThrow(new AforoCompletoException("El aforo de la zona para la fecha seleccionada ya fue alcanzado"))
                .when(aforoService).validarAforoDisponible(destino, FECHA_VISITA, 1);

        assertThatThrownBy(() -> informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(30), null))
                .isInstanceOf(AforoCompletoException.class);

        verify(informePlanificacionRepository, never()).save(any());
        verify(informeVisitanteRepository, never()).save(any());
    }

    /**
     * La descarga en PDF de un informe ya emitido no vuelve a descontar el
     * aforo: ver el HTML y bajar el PDF consumirian dos cupos.
     */
    @Test
    void laPrevisualizacionNoConsumeAforoNiPersisteInforme() {
        when(climaService.buscarPorEstacionYFecha(4, FECHA_VISITA)).thenReturn(Optional.empty());

        InformeConsolidadoDTO informe = informeService.previsualizarInforme(
                origen, destino, servicio, FECHA_VISITA, grupo(38, 36, 8), "INF-0008");

        assertThat(informe.getCodigo()).isEqualTo("INF-0008");
        // El PDF debe repetir el mismo total que vio el turista en el HTML.
        assertThat(informe.getTotalEstimado()).isEqualByComparingTo("550.10");
        verify(aforoService, never()).validarAforoDisponible(any(), any(), anyInt());
        verify(aforoService, never()).validarAforoDisponible(any(), any());
        verify(informePlanificacionRepository, never()).save(any());
        verify(informeVisitanteRepository, never()).save(any());
    }

    /** Una edad imposible se rechaza antes de tocar el aforo o la base. */
    @Test
    void rechazaLasEdadesFueraDeRango() {
        assertThatThrownBy(() -> informeService.generarInformeConsolidado(
                origen, destino, servicio, FECHA_VISITA, grupo(130), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Edad fuera de rango");

        verify(aforoService, never()).validarAforoDisponible(any(), any(), anyInt());
        verify(informePlanificacionRepository, never()).save(any());
    }
}
