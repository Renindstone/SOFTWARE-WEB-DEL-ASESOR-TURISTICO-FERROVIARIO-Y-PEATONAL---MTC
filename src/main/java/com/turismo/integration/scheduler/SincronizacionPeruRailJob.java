package com.turismo.integration.scheduler;

import com.turismo.exception.TarifaInvalidaException;
import com.turismo.integration.perurail.PeruRailClient;
import com.turismo.integration.perurail.dto.EstacionPeruRailDTO;
import com.turismo.integration.perurail.dto.ServicioTrenPeruRailDTO;
import com.turismo.model.Estacion;
import com.turismo.model.ServicioTren;
import com.turismo.repository.EstacionRepository;
import com.turismo.repository.ServicioTrenRepository;
import com.turismo.service.AuditoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * RF-14/RNF-03: sincroniza periodicamente estaciones, horarios y tarifas
 * desde PeruRail, y registra la sincronizacion en AuditoriaLog (RF-16).
 */
@Component
public class SincronizacionPeruRailJob {

    private static final Logger LOG = LoggerFactory.getLogger(SincronizacionPeruRailJob.class);

    private final PeruRailClient peruRailClient;
    private final EstacionRepository estacionRepository;
    private final ServicioTrenRepository servicioTrenRepository;
    private final AuditoriaService auditoriaService;

    public SincronizacionPeruRailJob(PeruRailClient peruRailClient,
                                      EstacionRepository estacionRepository,
                                      ServicioTrenRepository servicioTrenRepository,
                                      AuditoriaService auditoriaService) {
        this.peruRailClient = peruRailClient;
        this.estacionRepository = estacionRepository;
        this.servicioTrenRepository = servicioTrenRepository;
        this.auditoriaService = auditoriaService;
    }

    @Scheduled(cron = "${integracion.perurail.cron:0 0 3 * * *}")
    @Transactional
    public void sincronizar() {
        int estaciones = sincronizarEstaciones();
        int servicios = sincronizarServicios();

        auditoriaService.registrarSincronizacion("estacion",
                "Sincronizacion PeruRail: " + estaciones + " estaciones actualizadas");
        auditoriaService.registrarSincronizacion("servicio_tren",
                "Sincronizacion PeruRail: " + servicios + " servicios actualizados");

        LOG.info("Sincronización PeruRail finalizada: {} estaciones, {} servicios", estaciones, servicios);
    }

    /**
     * Alta o actualizacion por EstCodigo (campo UNIQUE del feed). No se toca
     * EstEstado: darla de baja es una decision administrativa del MTC, no del
     * feed, y el caso de prueba CN-02 depende de que Poroy siga inactiva.
     */
    private int sincronizarEstaciones() {
        List<EstacionPeruRailDTO> feed = peruRailClient.obtenerEstaciones();
        int procesadas = 0;
        for (EstacionPeruRailDTO dto : feed) {
            if (dto.getCodigo() == null || dto.getLatitud() == null || dto.getLongitud() == null) {
                LOG.warn("Estación de PeruRail incompleta ({}); se omite", dto.getCodigo());
                continue;
            }
            Estacion estacion = estacionRepository.findByCodigo(dto.getCodigo())
                    .orElseGet(Estacion::new);
            estacion.setCodigo(dto.getCodigo());
            estacion.setNombre(dto.getNombre());
            estacion.setLatitud(dto.getLatitud());
            estacion.setLongitud(dto.getLongitud());
            estacion.setAltitud(dto.getAltitud());
            estacion.setCiudad(dto.getCiudad());
            if (estacion.getEstado() == null) {
                estacion.setEstado("Activa");
            }
            estacionRepository.save(estacion);
            procesadas++;
        }
        return procesadas;
    }

    /**
     * CB-05/CB-06: cada tarifa pasa por validarTarifaPeruRail antes de
     * persistirse; un renglon con tarifa invalida se descarta sin abortar el
     * resto del lote.
     */
    private int sincronizarServicios() {
        List<ServicioTrenPeruRailDTO> feed = peruRailClient.obtenerServicios();
        int procesados = 0;
        for (ServicioTrenPeruRailDTO dto : feed) {
            Optional<Estacion> origen = estacionRepository.findByCodigo(dto.getCodigoEstacionOrigen());
            Optional<Estacion> destino = estacionRepository.findByCodigo(dto.getCodigoEstacionDestino());
            if (origen.isEmpty() || destino.isEmpty()) {
                LOG.warn("Servicio de PeruRail con estaciones desconocidas ({} -> {}); se omite",
                        dto.getCodigoEstacionOrigen(), dto.getCodigoEstacionDestino());
                continue;
            }
            try {
                peruRailClient.validarTarifaPeruRail(dto.getTarifa());
            } catch (TarifaInvalidaException ex) {
                LOG.warn("Tarifa inválida en el feed de PeruRail ({} -> {}): {}",
                        dto.getCodigoEstacionOrigen(), dto.getCodigoEstacionDestino(), ex.getMessage());
                continue;
            }

            // Clave natural del feed (tramo + hora de salida), la misma que
            // protege uq_servicio_tramo_hora: sin esta busqueda, cada
            // ejecucion volvia a insertar el catalogo entero.
            LocalTime horarioSalida = parsearHora(dto.getHorarioSalida());
            ServicioTren servicio = servicioTrenRepository
                    .findByEstacionOrigen_IdAndEstacionDestino_IdAndHorarioSalida(
                            origen.get().getId(), destino.get().getId(), horarioSalida)
                    .orElseGet(ServicioTren::new);
            servicio.setEstacionOrigen(origen.get());
            servicio.setEstacionDestino(destino.get());
            servicio.setHorarioSalida(horarioSalida);
            servicio.setHorarioLlegada(parsearHora(dto.getHorarioLlegada()));
            servicio.setTiempoTransitoMin(dto.getTiempoTransitoMin());
            servicio.setTarifa(dto.getTarifa());
            servicioTrenRepository.save(servicio);
            procesados++;
        }
        return procesados;
    }

    private LocalTime parsearHora(String hora) {
        try {
            return LocalTime.parse(hora);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new IllegalArgumentException("Horario inválido en el feed de PeruRail: " + hora, ex);
        }
    }
}
