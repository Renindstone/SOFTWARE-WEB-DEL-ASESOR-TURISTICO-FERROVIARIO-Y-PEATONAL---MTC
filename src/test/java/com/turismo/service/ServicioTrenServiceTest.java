package com.turismo.service;

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

        servicioTrenService.eliminar(10, "rail_luis");

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
    @DisplayName("Eliminar servicio inexistente: no ejecuta delete ni registra auditoria")
    void eliminar_servicioNoExiste_noHaceNada() {
        when(servicioTrenRepository.buscarConEstaciones(999)).thenReturn(Optional.empty());

        servicioTrenService.eliminar(999, "rail_luis");

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
}

