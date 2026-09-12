package com.turismo.integration.scheduler;

import com.turismo.exception.FeedInvalidoException;
import com.turismo.integration.senamhi.SenamhiClient;
import com.turismo.integration.senamhi.dto.PrevisionClimaSenamhiDTO;
import com.turismo.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * RF-15/RNF-03: sincroniza diariamente las previsiones climaticas del
 * SENAMHI por estacion, y registra la sincronizacion en AuditoriaLog (RF-16).
 */
@Component
public class SincronizacionSenamhiJob {

    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionSenamhiJob.class);

    private final SenamhiClient senamhiClient;
    private final AuditoriaService auditoriaService;

    public SincronizacionSenamhiJob(SenamhiClient senamhiClient, AuditoriaService auditoriaService) {
        this.senamhiClient = senamhiClient;
        this.auditoriaService = auditoriaService;
    }

    /**
     * CB-04: un registro fuera de rango se descarta y la tarea continua con
     * el resto del lote, en vez de perder toda la previsión del día.
     */
    @Scheduled(cron = "${integracion.senamhi.cron:0 0 4 * * *}")
    @Transactional
    public void sincronizar() {
        List<PrevisionClimaSenamhiDTO> feed = senamhiClient.obtenerPrevisiones();
        int procesadas = 0;
        int descartadas = 0;

        for (PrevisionClimaSenamhiDTO prevision : feed) {
            try {
                senamhiClient.procesarFeedSenamhi(prevision);
                procesadas++;
            } catch (FeedInvalidoException ex) {
                descartadas++;
                LOG.warn("Previsión del SENAMHI descartada para la estación {}: {}",
                        prevision.getCodigoEstacion(), ex.getMessage());
            }
        }

        auditoriaService.registrarSincronizacion("prevision_clima",
                "Sincronizacion SENAMHI: " + procesadas + " previsiones actualizadas, "
                        + descartadas + " descartadas");

        LOG.info("Sincronización SENAMHI finalizada: {} previsiones, {} descartadas", procesadas, descartadas);
    }
}
