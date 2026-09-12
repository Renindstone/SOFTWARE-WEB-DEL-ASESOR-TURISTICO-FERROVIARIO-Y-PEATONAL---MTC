package com.turismo.service;

import com.turismo.dto.InformeConsolidadoDTO;
import com.turismo.dto.RutaCalculadaDTO;
import com.turismo.dto.VisitanteDTO;
import com.turismo.model.CategoriaVisitante;
import com.turismo.model.Estacion;
import com.turismo.model.InformePlanificacion;
import com.turismo.model.InformeVisitante;
import com.turismo.model.PrevisionClima;
import com.turismo.model.RutaPeatonal;
import com.turismo.model.ServicioTren;
import com.turismo.model.Usuario;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.InformePlanificacionRepository;
import com.turismo.repository.InformeVisitanteRepository;
import com.turismo.util.GeneradorPdf;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RF-08/RF-18: genera el informe consolidado (ruta, clima, tiempo estimado,
 * dificultad y tarifas del grupo) en formato web/PDF descargable.
 * Orquesta RutaPeatonalService, ClimaService, TarifaService, AforoService
 * (RF-16/RF-17) y AuditoriaService antes de persistir InformePlanificacion
 * junto con la composicion del grupo.
 */
@Service
public class InformeService {

    private static final String PREFIJO_CODIGO = "INF-";
    private static final String TABLA_AUDITADA = "informe_planificacion";

    /** Grupo por defecto cuando no se declara ninguna edad: un adulto. */
    private static final int EDAD_ADULTA_POR_DEFECTO = 30;

    private final InformePlanificacionRepository informePlanificacionRepository;
    private final InformeVisitanteRepository informeVisitanteRepository;
    private final RutaPeatonalService rutaPeatonalService;
    private final ClimaService climaService;
    private final AforoService aforoService;
    private final TarifaService tarifaService;
    private final AuditoriaService auditoriaService;
    private final GeneradorPdf generadorPdf;

    public InformeService(InformePlanificacionRepository informePlanificacionRepository,
                           InformeVisitanteRepository informeVisitanteRepository,
                           RutaPeatonalService rutaPeatonalService,
                           ClimaService climaService,
                           AforoService aforoService,
                           TarifaService tarifaService,
                           AuditoriaService auditoriaService,
                           GeneradorPdf generadorPdf) {
        this.informePlanificacionRepository = informePlanificacionRepository;
        this.informeVisitanteRepository = informeVisitanteRepository;
        this.rutaPeatonalService = rutaPeatonalService;
        this.climaService = climaService;
        this.aforoService = aforoService;
        this.tarifaService = tarifaService;
        this.auditoriaService = auditoriaService;
        this.generadorPdf = generadorPdf;
    }

    /**
     * RF-08/RF-16/RF-18 (CU-08): valida el aforo para TODAS las personas del
     * grupo, calcula y persiste la ruta, arma el informe con las tarifas por
     * edad y guarda el InformePlanificacion con su detalle de visitantes. Si
     * no queda sitio para el grupo completo, AforoCompletoException
     * interrumpe la generacion antes de consumir cupo.
     */
    @Transactional
    public InformeConsolidadoDTO generarInformeConsolidado(Estacion origen, ZonaTuristica destino,
                                                            ServicioTren servicioTren, LocalDate fechaVisita,
                                                            List<VisitanteDTO> grupo, Usuario usuario) {
        List<VisitanteDTO> visitantes = normalizarGrupo(grupo);
        int personas = contarPersonas(visitantes);

        aforoService.validarAforoDisponible(destino, fechaVisita, personas);

        InformeConsolidadoDTO informe = prepararInforme(origen, destino, servicioTren, fechaVisita, visitantes);
        RutaPeatonal ruta = rutaPeatonalService.obtenerOCrearRuta(origen, destino, informe.getRuta());

        InformePlanificacion registro = new InformePlanificacion();
        registro.setCodigo(generarCodigo());
        registro.setFechaEmision(LocalDateTime.now());
        registro.setFechaVisita(fechaVisita);
        registro.setUsuario(usuario);
        registro.setRuta(ruta);
        registro.setTotalEstimado(informe.getTotalEstimado());
        informePlanificacionRepository.save(registro);

        persistirVisitantes(registro, destino, servicioTren, informe.getVisitantes());

        informe.setCodigo(registro.getCodigo());

        auditoriaService.registrarAuditoria(
                usuario == null ? "ANONIMO" : usuario.getNombreUsuario(),
                "INSERT", TABLA_AUDITADA, null,
                "InfCodigo=" + registro.getCodigo()
                        + "; ZonNombre=" + destino.getNombre()
                        + "; InfFechaVisita=" + fechaVisita
                        + "; personas=" + personas);

        return informe;
    }

    /**
     * Vista previa del informe: mismos datos, pero sin consumir cupo de aforo
     * ni persistir el InformePlanificacion. La usa la exportacion a PDF de un
     * informe ya emitido, para que ver el HTML y descargar el PDF no descuente
     * el aforo dos veces.
     */
    @Transactional(readOnly = true)
    public InformeConsolidadoDTO previsualizarInforme(Estacion origen, ZonaTuristica destino,
                                                       ServicioTren servicioTren, LocalDate fechaVisita,
                                                       List<VisitanteDTO> grupo, String codigoExistente) {
        InformeConsolidadoDTO informe = prepararInforme(
                origen, destino, servicioTren, fechaVisita, normalizarGrupo(grupo));
        if (codigoExistente != null && !codigoExistente.isBlank()) {
            informe.setCodigo(codigoExistente);
        }
        return informe;
    }

    private InformeConsolidadoDTO prepararInforme(Estacion origen, ZonaTuristica destino,
                                                   ServicioTren servicioTren, LocalDate fechaVisita,
                                                   List<VisitanteDTO> visitantes) {
        RutaCalculadaDTO ruta = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(origen, destino);
        Optional<PrevisionClima> clima = climaService.buscarPorEstacionYFecha(origen.getId(), fechaVisita);

        InformeConsolidadoDTO informe = new InformeConsolidadoDTO();
        informe.setFechaVisita(fechaVisita);
        informe.setEstacionOrigen(origen.getNombre());
        informe.setZonaDestino(destino.getNombre());
        informe.setRuta(ruta);
        clima.ifPresent(c -> {
            informe.setTemperaturaMinimaC(c.getTemperaturaMinC());
            informe.setTemperaturaMaximaC(c.getTemperaturaMaxC());
            informe.setProbabilidadLluvia(c.getProbabilidadLluvia());
            informe.setEstadoClima(c.getEstadoClima());
        });

        // Tarifas base por persona; el total sale de aplicarlas a cada edad.
        BigDecimal tarifaTren = servicioTren == null ? null : servicioTren.getTarifa();
        informe.setTarifaTren(tarifaTren);
        informe.setServicioTren(describirServicio(servicioTren));
        informe.setCostoZona(destino.getCostoAprox());

        aplicarTarifas(visitantes, tarifaTren, destino.getCostoAprox());
        informe.setVisitantes(visitantes);
        informe.setTotalEstimado(sumarTotal(visitantes));

        aforoService.consultarCupoDisponible(destino, fechaVisita)
                .ifPresent(informe::setCupoDisponible);
        return informe;
    }

    /**
     * RF-18: a cada linea del grupo se le asigna la categoria que le toca en
     * cada ambito y su subtotal. Los cortes de edad los decide TarifaService
     * a partir de la tabla parametrica, no este metodo.
     */
    private void aplicarTarifas(List<VisitanteDTO> visitantes, BigDecimal tarifaTren, BigDecimal costoZona) {
        for (VisitanteDTO visitante : visitantes) {
            int edad = visitante.getEdad();
            int cantidad = visitante.getCantidad();

            CategoriaVisitante categoriaZona =
                    tarifaService.categoriaPorEdad(CategoriaVisitante.AMBITO_ZONA, edad);
            visitante.setCategoriaZona(categoriaZona.getNombre());
            visitante.setSubtotalZona(tarifaService.calcularSubtotal(costoZona, categoriaZona, cantidad));

            if (tarifaTren == null) {
                visitante.setCategoriaTren(null);
                visitante.setSubtotalTren(BigDecimal.ZERO);
                continue;
            }
            CategoriaVisitante categoriaTren =
                    tarifaService.categoriaPorEdad(CategoriaVisitante.AMBITO_TREN, edad);
            visitante.setCategoriaTren(categoriaTren.getNombre());
            visitante.setSubtotalTren(tarifaService.calcularSubtotal(tarifaTren, categoriaTren, cantidad));
        }
    }

    private void persistirVisitantes(InformePlanificacion registro, ZonaTuristica destino,
                                      ServicioTren servicioTren, List<VisitanteDTO> visitantes) {
        for (VisitanteDTO visitante : visitantes) {
            InformeVisitante fila = new InformeVisitante();
            fila.setInforme(registro);
            fila.setEdad(visitante.getEdad().shortValue());
            fila.setCantidad(visitante.getCantidad().shortValue());
            fila.setCategoriaZona(tarifaService.categoriaPorEdad(
                    CategoriaVisitante.AMBITO_ZONA, visitante.getEdad()));
            if (servicioTren != null) {
                fila.setCategoriaTren(tarifaService.categoriaPorEdad(
                        CategoriaVisitante.AMBITO_TREN, visitante.getEdad()));
            }
            fila.setSubtotalTren(visitante.getSubtotalTren());
            fila.setSubtotalZona(visitante.getSubtotalZona());
            informeVisitanteRepository.save(fila);
        }
    }

    /**
     * Agrupa por edad y descarta lineas vacias, porque la BD exige una sola
     * fila por edad dentro del informe (uq_visitante_informe_edad). Si no
     * llega ningun dato, se asume un adulto: el turista que consulta solo.
     */
    private List<VisitanteDTO> normalizarGrupo(List<VisitanteDTO> grupo) {
        Map<Integer, Integer> personasPorEdad = new LinkedHashMap<>();
        if (grupo != null) {
            for (VisitanteDTO visitante : grupo) {
                if (visitante == null || visitante.getEdad() == null) {
                    continue;
                }
                int cantidad = visitante.getCantidad() == null ? 1 : visitante.getCantidad();
                if (cantidad <= 0) {
                    continue;
                }
                if (visitante.getEdad() < 0 || visitante.getEdad() > 120) {
                    throw new IllegalArgumentException(
                            "Edad fuera de rango: " + visitante.getEdad());
                }
                personasPorEdad.merge(visitante.getEdad(), cantidad, Integer::sum);
            }
        }
        if (personasPorEdad.isEmpty()) {
            personasPorEdad.put(EDAD_ADULTA_POR_DEFECTO, 1);
        }

        List<VisitanteDTO> normalizado = new ArrayList<>();
        personasPorEdad.forEach((edad, cantidad) -> normalizado.add(new VisitanteDTO(edad, cantidad)));
        return normalizado;
    }

    /** "Ollantaytambo → Machu Picchu, salida 08:53": el tren al que corresponde la tarifa. */
    private String describirServicio(ServicioTren servicio) {
        if (servicio == null) {
            return null;
        }
        String origen = servicio.getEstacionOrigen() == null ? "?" : servicio.getEstacionOrigen().getNombre();
        String destino = servicio.getEstacionDestino() == null ? "?" : servicio.getEstacionDestino().getNombre();
        return origen + " → " + destino + ", salida " + servicio.getHorarioSalida();
    }

    private int contarPersonas(List<VisitanteDTO> visitantes) {
        return visitantes.stream().mapToInt(VisitanteDTO::getCantidad).sum();
    }

    private BigDecimal sumarTotal(List<VisitanteDTO> visitantes) {
        return visitantes.stream()
                .map(VisitanteDTO::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Codigo correlativo con el formato INF-0001 del diccionario de datos
     * (InfCodigo, VARCHAR(15)). Se deriva del ultimo codigo persistido, de
     * modo que sobreviva a los reinicios de la aplicacion.
     */
    private String generarCodigo() {
        int siguiente = informePlanificacionRepository.findTopByOrderByIdDesc()
                .map(InformePlanificacion::getCodigo)
                .map(this::extraerCorrelativo)
                .orElse(0) + 1;
        return String.format("%s%04d", PREFIJO_CODIGO, siguiente);
    }

    private int extraerCorrelativo(String codigo) {
        String digitos = codigo.replaceAll("\\D", "");
        return digitos.isEmpty() ? 0 : Integer.parseInt(digitos);
    }

    public byte[] exportarPdf(InformeConsolidadoDTO informe) {
        return generadorPdf.generar(informe);
    }

    @Transactional(readOnly = true)
    public List<InformePlanificacion> obtenerInformesPorUsuario(String nombreUsuario) {
        return informePlanificacionRepository.findByUsuario_NombreUsuarioOrderByFechaEmisionDesc(nombreUsuario);
    }
}
