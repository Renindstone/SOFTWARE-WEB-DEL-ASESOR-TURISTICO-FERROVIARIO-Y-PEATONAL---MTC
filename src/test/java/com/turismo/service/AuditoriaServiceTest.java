package com.turismo.service;

import com.turismo.model.AuditoriaLog;
import com.turismo.repository.AuditoriaLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja Blanca: CB-07 (registrarAuditoria - persiste valor anterior y
 * nuevo sin nulos para una operacion UPDATE).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaLogRepository auditoriaLogRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    /** CB-07: UPDATE con valor anterior y nuevo, ambos presentes. */
    @Test
    void cb07_registraLaOperacionConValorAnteriorYNuevo() {
        when(auditoriaLogRepository.save(any(AuditoriaLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriaLog resultado = auditoriaService.registrarAuditoria(
                "admin_mtc", "UPDATE", "estacion", "EstEstado=Activa", "EstEstado=Inactiva");

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());

        assertThat(captor.getValue().getOperacion()).isEqualTo("UPDATE");
        assertThat(captor.getValue().getUsuario()).isEqualTo("admin_mtc");
        assertThat(captor.getValue().getFecha()).isNotNull();
        assertThat(captor.getValue().getValorAnterior()).isNotNull();
        assertThat(captor.getValue().getValorNuevo()).isNotNull();
        assertThat(resultado.getTablaAfectada()).isEqualTo("estacion");
    }

    /** RF-16: las sincronizaciones automaticas se registran como SYNC / SISTEMA. */
    @Test
    void registraLasSincronizacionesComoOperacionSyncDelSistema() {
        when(auditoriaLogRepository.save(any(AuditoriaLog.class))).thenAnswer(inv -> inv.getArgument(0));

        auditoriaService.registrarSincronizacion("prevision_clima",
                "Sincronizacion SENAMHI: 15 previsiones actualizadas");

        ArgumentCaptor<AuditoriaLog> captor = ArgumentCaptor.forClass(AuditoriaLog.class);
        verify(auditoriaLogRepository).save(captor.capture());

        assertThat(captor.getValue().getOperacion()).isEqualTo("SYNC");
        assertThat(captor.getValue().getUsuario()).isEqualTo(AuditoriaService.USUARIO_SISTEMA);
        assertThat(captor.getValue().getValorAnterior()).isNull();
    }

    /**
     * AudValorAnterior/AudValorNuevo son VARCHAR(500) en el diccionario de
     * datos: un valor mas largo se recorta en vez de romper el INSERT.
     */
    @Test
    void recortaLosValoresQueSuperanElLimiteDelDiccionarioDeDatos() {
        when(auditoriaLogRepository.save(any(AuditoriaLog.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditoriaLog resultado = auditoriaService.registrarAuditoria(
                "travel_ana", "UPDATE", "zona_turistica", "x".repeat(600), "y".repeat(600));

        assertThat(resultado.getValorAnterior()).hasSize(500);
        assertThat(resultado.getValorNuevo()).hasSize(500);
    }
}
