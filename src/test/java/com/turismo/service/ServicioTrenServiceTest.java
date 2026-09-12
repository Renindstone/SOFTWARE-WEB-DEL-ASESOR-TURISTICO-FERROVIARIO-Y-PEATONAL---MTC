package com.turismo.service;

import com.turismo.exception.ServicioTrenInvalidoException;
import com.turismo.integration.perurail.PeruRailClient;
import com.turismo.model.Estacion;
import com.turismo.model.ServicioTren;
import com.turismo.repository.ServicioTrenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Caja Blanca: CB-12 (eliminar - borrado fisico de servicio existente y
 * registro de traza en AuditoriaLog con operacion DELETE).
 *
 * Valida el mantenimiento de servicios de tren (RF-12, RF-15), incluyendo
 * el alta con validacion de tarifa (CB-05) y la eliminacion de registros.
 */
@ExtendWith(MockitoExtension.class)
class ServicioTrenServiceTest {

    @Mock
    private ServicioTrenRepository servicioTrenRepository;

    @Mock
    private PeruRailClient peruRailClient;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ServicioTrenService servicioTrenService;

    private Estacion estacionOrigen;
    private Estacion estacionDestino;
    private ServicioTren servicioExistente;

    @BeforeEach
    void setUp() {
        estacionOrigen = new Estacion();
        estacionOrigen.setId(1);
        estacionOrigen.setNombre("San Pedro");

        estacionDestino = new Estacion();
        estacionDestino.setId(2);
        estacionDestino.setNombre("Ollantaytambo");

        servicioExistente = new ServicioTren();
        servicioExistente.setId(10);
        servicioExistente.setEstacionOrigen(estacionOrigen);
        servicioExistente.setEstacionDestino(estacionDestino);
        servicioExistente.setHorarioSalida(LocalTime.of(8, 30));
        servicioExistente.setHorarioLlegada(LocalTime.of(10, 45));
        servicioExistente.setTiempoTransitoMin(135);
        servicioExistente.setTarifa(new BigDecimal("120.00"));
    }

    /** CB-12: servicio existente -> borrado de BD y registro en AuditoriaLog con AudOperacion='DELETE'. */
    @Test
    @DisplayName("CB-12: Eliminar servicio existente: borra de repositorio y registra auditoria DELETE")
    void cb12_eliminarServicioExistente_borraYRegistraAuditoria() {
        when(servicioTrenRepository.buscarConEstaciones(10)).thenReturn(Optional.of(servicioExistente));

        boolean eliminado = servicioTrenService.eliminar(10, "rail_luis");

        assertThat(eliminado).isTrue();
        verify(servicioTrenRepository).delete(servicioExistente);

        ArgumentCaptor<String> captorValorAnterior = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrarAuditoria(
                eq("rail_luis"),
                eq("DELETE"),
                eq("servicio_tren"),
                captorValorAnterior.capture(),
                isNull()
        );

        assertThat(captorValorAnterior.getValue())
                .contains("SerIdEstacionOrigen=1")
                .contains("SerIdEstacionDestino=2")
                .contains("SerTarifa=120.00");
    }

    @Test
    @DisplayName("Eliminar servicio inexistente: devuelve false, sin delete ni auditoria")
    void eliminar_servicioNoExiste_noHaceNada() {
        when(servicioTrenRepository.buscarConEstaciones(999)).thenReturn(Optional.empty());

        boolean eliminado = servicioTrenService.eliminar(999, "rail_luis");

        // El controlador se apoya en este false para no anunciar una baja que
        // no ha ocurrido (reenvio del formulario desde una pestana caducada).
        assertThat(eliminado).isFalse();
        verify(servicioTrenRepository, never()).delete(any());
        verify(auditoriaService, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Guardar nuevo servicio: valida tarifa, persiste y registra auditoria INSERT")
    void guardar_nuevoServicio_validaYRegistraInsert() {
        ServicioTren nuevo = new ServicioTren();
        nuevo.setEstacionOrigen(estacionOrigen);
        nuevo.setEstacionDestino(estacionDestino);
        nuevo.setTarifa(new BigDecimal("95.00"));

        when(servicioTrenRepository.save(nuevo)).thenReturn(servicioExistente);

        ServicioTren resultado = servicioTrenService.guardar(nuevo, "admin_mtc");

        verify(peruRailClient).validarTarifaPeruRail(new BigDecimal("95.00"));
        verify(servicioTrenRepository).save(nuevo);
        verify(auditoriaService).registrarAuditoria(
                eq("admin_mtc"),
                eq("INSERT"),
                eq("servicio_tren"),
                isNull(),
                anyString()
        );
        assertThat(resultado).isNotNull();
    }

    // ------------------------------------------------------------------
    // RF-12: reglas que relacionan varios campos entre si y que Bean
    // Validation no puede expresar campo a campo.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Guardar con la misma hora de salida y de llegada: rechaza y no persiste")
    void guardar_salidaIgualALlegada_rechaza() {
        ServicioTren instantaneo = new ServicioTren();
        instantaneo.setEstacionOrigen(estacionOrigen);
        instantaneo.setEstacionDestino(estacionDestino);
        instantaneo.setHorarioSalida(LocalTime.of(8, 30));
        instantaneo.setHorarioLlegada(LocalTime.of(8, 30));
        instantaneo.setTarifa(new BigDecimal("95.00"));

        // Sin esta regla se derivaria un transito de 1440 minutos (24 h).
        assertThatThrownBy(() -> servicioTrenService.guardar(instantaneo, "admin_mtc"))
                .isInstanceOf(ServicioTrenInvalidoException.class)
                .hasMessageContaining("distinta de la hora de salida");

        verify(servicioTrenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Guardar con origen igual al destino: rechaza y no persiste")
    void guardar_origenIgualADestino_rechaza() {
        ServicioTren circular = new ServicioTren();
        circular.setEstacionOrigen(estacionOrigen);
        circular.setEstacionDestino(estacionOrigen);
        circular.setHorarioSalida(LocalTime.of(8, 30));
        circular.setHorarioLlegada(LocalTime.of(10, 45));
        circular.setTiempoTransitoMin(135);
        circular.setTarifa(new BigDecimal("95.00"));

        assertThatThrownBy(() -> servicioTrenService.guardar(circular, "admin_mtc"))
                .isInstanceOf(ServicioTrenInvalidoException.class)
                .hasMessageContaining("distinta de la de origen");

        verify(servicioTrenRepository, never()).save(any());
        verify(auditoriaService, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    /** Cusco (San Pedro) y Machu Picchu, con sus coordenadas reales: 71.6 km en linea recta. */
    private void darCoordenadasCuscoMachuPicchu() {
        estacionOrigen.setLatitud(new BigDecimal("-13.522500"));
        estacionOrigen.setLongitud(new BigDecimal("-71.982200"));
        estacionDestino.setNombre("Machu Picchu");
        estacionDestino.setLatitud(new BigDecimal("-13.154700"));
        estacionDestino.setLongitud(new BigDecimal("-72.525000"));
    }

    @Test
    @DisplayName("Guardar un horario físicamente imposible (Cusco - Machu Picchu en 20 min): rechaza")
    void guardar_velocidadImposible_rechaza() {
        darCoordenadasCuscoMachuPicchu();
        ServicioTren relampago = new ServicioTren();
        relampago.setEstacionOrigen(estacionOrigen);
        relampago.setEstacionDestino(estacionDestino);
        relampago.setHorarioSalida(LocalTime.of(6, 10));
        relampago.setHorarioLlegada(LocalTime.of(6, 30));
        relampago.setTarifa(new BigDecimal("210.00"));

        // Llegada = salida + transito, asi que validarHorarios lo daria por
        // bueno; son los 89 km de via en 20 minutos (268 km/h) lo que no cuadra.
        assertThatThrownBy(() -> servicioTrenService.guardar(relampago, "admin_mtc"))
                .isInstanceOf(ServicioTrenInvalidoException.class)
                .hasMessageContaining("268 km/h")
                .hasMessageContaining("80 km/h");

        verify(servicioTrenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Guardar el horario real del corredor (Cusco - Machu Picchu en 224 min): acepta")
    void guardar_velocidadRealDelCorredor_acepta() {
        darCoordenadasCuscoMachuPicchu();
        ServicioTren expedition = new ServicioTren();
        expedition.setEstacionOrigen(estacionOrigen);
        expedition.setEstacionDestino(estacionDestino);
        expedition.setHorarioSalida(LocalTime.of(6, 10));
        expedition.setHorarioLlegada(LocalTime.of(9, 54));
        expedition.setTarifa(new BigDecimal("210.00"));
        when(servicioTrenRepository.save(expedition)).thenReturn(expedition);

        // 89 km en 224 minutos son 24 km/h: dentro de lo que la red alcanza.
        servicioTrenService.guardar(expedition, "admin_mtc");

        verify(servicioTrenRepository).save(expedition);
    }

    @Test
    @DisplayName("Guardar con llegada que no concuerda con salida mas transito: rechaza")
    void guardar_horariosIncoherentes_rechaza() {
        ServicioTren incoherente = new ServicioTren();
        incoherente.setEstacionOrigen(estacionOrigen);
        incoherente.setEstacionDestino(estacionDestino);
        incoherente.setHorarioSalida(LocalTime.of(8, 30));
        incoherente.setHorarioLlegada(LocalTime.of(9, 0));   // 30 min, no 135
        incoherente.setTiempoTransitoMin(135);
        incoherente.setTarifa(new BigDecimal("95.00"));

        assertThatThrownBy(() -> servicioTrenService.guardar(incoherente, "admin_mtc"))
                .isInstanceOf(ServicioTrenInvalidoException.class)
                .hasMessageContaining("10:45");

        verify(servicioTrenRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // El tiempo de transito es redundante: se deriva de los dos horarios.
    // El formulario lo calcula en el navegador y lo envia en un campo de
    // solo lectura, pero el servicio tiene que resolverlo igual para quien
    // no pase por esa pantalla.
    // ------------------------------------------------------------------

    @Test
    @DisplayName("La entidad no exige el tránsito: es derivable y el enlazado no debe rechazarlo")
    void entidad_noExigeTiempoTransito() throws Exception {
        // Si volviera a llevar @NotNull o @Positive, el enlazado del formulario
        // rechazaria el envio antes de llegar al servicio y la derivacion del
        // servidor no llegaria a ejecutarse: el administrador se quedaria ante
        // un campo obligatorio que ademas es de solo lectura.
        var campo = ServicioTren.class.getDeclaredField("tiempoTransitoMin");
        assertThat(campo.getAnnotation(jakarta.validation.constraints.NotNull.class)).isNull();
        assertThat(campo.getAnnotation(jakarta.validation.constraints.Positive.class)).isNull();

        // Los horarios si siguen siendo obligatorios: son de donde se deriva.
        assertThat(ServicioTren.class.getDeclaredField("horarioSalida")
                .getAnnotation(jakarta.validation.constraints.NotNull.class)).isNotNull();
        assertThat(ServicioTren.class.getDeclaredField("horarioLlegada")
                .getAnnotation(jakarta.validation.constraints.NotNull.class)).isNotNull();
    }

    @Test
    @DisplayName("Guardar sin tiempo de tránsito: lo deriva de los horarios")
    void guardar_sinTransito_loDeriva() {
        ServicioTren sinTransito = new ServicioTren();
        sinTransito.setEstacionOrigen(estacionOrigen);
        sinTransito.setEstacionDestino(estacionDestino);
        sinTransito.setHorarioSalida(LocalTime.of(8, 30));
        sinTransito.setHorarioLlegada(LocalTime.of(10, 45));
        sinTransito.setTiempoTransitoMin(null);
        sinTransito.setTarifa(new BigDecimal("95.00"));

        when(servicioTrenRepository.save(sinTransito)).thenReturn(sinTransito);

        servicioTrenService.guardar(sinTransito, "admin_mtc");

        assertThat(sinTransito.getTiempoTransitoMin()).isEqualTo(135);
    }

    @Test
    @DisplayName("Guardar sin tránsito un servicio nocturno: da la vuelta al reloj")
    void guardar_sinTransitoNocturno_daLaVueltaAlReloj() {
        ServicioTren nocturno = new ServicioTren();
        nocturno.setEstacionOrigen(estacionOrigen);
        nocturno.setEstacionDestino(estacionDestino);
        nocturno.setHorarioSalida(LocalTime.of(21, 0));
        nocturno.setHorarioLlegada(LocalTime.of(8, 0));
        nocturno.setTiempoTransitoMin(null);
        nocturno.setTarifa(new BigDecimal("1200.00"));

        when(servicioTrenRepository.save(nocturno)).thenReturn(nocturno);

        servicioTrenService.guardar(nocturno, "admin_mtc");

        // 21:00 -> 08:00 son 660 minutos, no -780.
        assertThat(nocturno.getTiempoTransitoMin()).isEqualTo(660);
    }

    @Test
    @DisplayName("Guardar con tránsito en cero: lo recalcula en vez de rechazarlo")
    void guardar_transitoEnCero_loRecalcula() {
        ServicioTren enCero = new ServicioTren();
        enCero.setEstacionOrigen(estacionOrigen);
        enCero.setEstacionDestino(estacionDestino);
        enCero.setHorarioSalida(LocalTime.of(6, 10));
        enCero.setHorarioLlegada(LocalTime.of(9, 54));
        enCero.setTiempoTransitoMin(0);
        enCero.setTarifa(new BigDecimal("210.00"));

        when(servicioTrenRepository.save(enCero)).thenReturn(enCero);

        servicioTrenService.guardar(enCero, "admin_mtc");

        assertThat(enCero.getTiempoTransitoMin()).isEqualTo(224);
    }

    @Test
    @DisplayName("Guardar con un tránsito coherente ya puesto: no lo pisa")
    void guardar_conTransitoCoherente_loRespeta() {
        ServicioTren conTransito = new ServicioTren();
        conTransito.setEstacionOrigen(estacionOrigen);
        conTransito.setEstacionDestino(estacionDestino);
        conTransito.setHorarioSalida(LocalTime.of(8, 30));
        conTransito.setHorarioLlegada(LocalTime.of(10, 45));
        conTransito.setTiempoTransitoMin(135);
        conTransito.setTarifa(new BigDecimal("95.00"));

        when(servicioTrenRepository.save(conTransito)).thenReturn(conTransito);

        servicioTrenService.guardar(conTransito, "admin_mtc");

        assertThat(conTransito.getTiempoTransitoMin()).isEqualTo(135);
    }

    @Test
    @DisplayName("Guardar con llegada anterior a la salida: valido si es un servicio nocturno")
    void guardar_servicioNocturno_seAcepta() {
        // Tramo Puno - Arequipa de 02_datos.sql: sale 21:00, viaja 660 minutos
        // y llega a las 08:00 del dia siguiente. Comprobar solo que la llegada
        // fuese posterior a la salida habria rechazado este servicio real.
        ServicioTren nocturno = new ServicioTren();
        nocturno.setEstacionOrigen(estacionOrigen);
        nocturno.setEstacionDestino(estacionDestino);
        nocturno.setHorarioSalida(LocalTime.of(21, 0));
        nocturno.setHorarioLlegada(LocalTime.of(8, 0));
        nocturno.setTiempoTransitoMin(660);
        nocturno.setTarifa(new BigDecimal("1200.00"));

        when(servicioTrenRepository.save(nocturno)).thenReturn(nocturno);

        ServicioTren resultado = servicioTrenService.guardar(nocturno, "admin_mtc");

        assertThat(resultado).isNotNull();
        verify(servicioTrenRepository).save(nocturno);
    }
}

