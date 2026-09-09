package com.turismo.service;

import com.turismo.integration.perurail.PeruRailClient;
import com.turismo.model.ServicioTren;
import com.turismo.repository.ServicioTrenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * RF-12: mantenimiento de horarios y precios de los servicios de tren
 * (administrador de PeruRail/MTC). Respalda la vista admin/servicios-tren.html
 * y deja traza de cada cambio en AuditoriaLog (RF-15/RNF-07).
 */
@Service
public class ServicioTrenService {

    private static final String TABLA_AUDITADA = "servicio_tren";

    private final ServicioTrenRepository servicioTrenRepository;
    private final PeruRailClient peruRailClient;
    private final AuditoriaService auditoriaService;

    public ServicioTrenService(ServicioTrenRepository servicioTrenRepository,
                                PeruRailClient peruRailClient,
                                AuditoriaService auditoriaService) {
        this.servicioTrenRepository = servicioTrenRepository;
        this.peruRailClient = peruRailClient;
        this.auditoriaService = auditoriaService;
    }

    public List<ServicioTren> listarPorEstacionOrigen(Integer idEstacionOrigen) {
        return servicioTrenRepository.findByEstacionOrigen_Id(idEstacionOrigen);
    }

    /** RF-07: servicios que llegan a la estacion de partida, ordenados por tarifa. */
    public List<ServicioTren> listarHaciaEstacion(Integer idEstacionDestino) {
        return servicioTrenRepository.listarHaciaEstacion(idEstacionDestino);
    }

    public List<ServicioTren> listarTodos() {
        return servicioTrenRepository.listarConEstaciones();
    }

    public Optional<ServicioTren> buscarPorId(Integer id) {
        return servicioTrenRepository.buscarConEstaciones(id);
    }

    /** CB-05/CB-06: valida la tarifa (PeruRailClient.validarTarifaPeruRail) antes de persistir. */
    @Transactional
    public ServicioTren guardar(ServicioTren servicioTren, String usuario) {
        peruRailClient.validarTarifaPeruRail(servicioTren.getTarifa());

        boolean esAlta = servicioTren.getId() == null;
        String valorAnterior = esAlta ? null
                : servicioTrenRepository.findById(servicioTren.getId()).map(this::describir).orElse(null);

        ServicioTren guardado = servicioTrenRepository.save(servicioTren);

        auditoriaService.registrarAuditoria(usuario, esAlta ? "INSERT" : "UPDATE", TABLA_AUDITADA,
                valorAnterior, describir(guardado));

        return guardado;
    }

    @Transactional
    public void eliminar(Integer id, String usuario) {
        servicioTrenRepository.buscarConEstaciones(id).ifPresent(servicio -> {
            String valorAnterior = describir(servicio);
            servicioTrenRepository.delete(servicio);
            auditoriaService.registrarAuditoria(usuario, "DELETE", TABLA_AUDITADA, valorAnterior, null);
        });
    }

    private String describir(ServicioTren servicio) {
        return "SerIdEstacionOrigen=" + servicio.getEstacionOrigen().getId()
                + "; SerIdEstacionDestino=" + servicio.getEstacionDestino().getId()
                + "; SerHorarioSalida=" + servicio.getHorarioSalida()
                + "; SerHorarioLlegada=" + servicio.getHorarioLlegada()
                + "; SerTiempoTransitoMin=" + servicio.getTiempoTransitoMin()
                + "; SerTarifa=" + servicio.getTarifa();
    }
}
