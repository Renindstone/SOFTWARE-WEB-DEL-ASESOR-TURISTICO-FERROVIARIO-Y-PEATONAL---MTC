package com.turismo.service;

import com.turismo.model.Preferencia;
import com.turismo.model.ZonaPreferencia;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.PreferenciaRepository;
import com.turismo.repository.ZonaPreferenciaRepository;
import com.turismo.repository.ZonaTuristicaRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * RF-10/RF-17: CRUD de zonas turisticas para Travel Group Peru, incluyendo
 * la asignacion N:M con Preferencia (tabla ZonaPreferencia) y el campo
 * ZonCupoMaximoDiario usado por AforoService. Cada alta, modificacion y
 * baja queda registrada en AuditoriaLog (RF-15/RNF-07).
 */
@Service
public class ZonaTuristicaService {

    private static final String TABLA_AUDITADA = "zona_turistica";

    private final ZonaTuristicaRepository zonaTuristicaRepository;
    private final ZonaPreferenciaRepository zonaPreferenciaRepository;
    private final PreferenciaRepository preferenciaRepository;
    private final AuditoriaService auditoriaService;

    public ZonaTuristicaService(ZonaTuristicaRepository zonaTuristicaRepository,
                                 ZonaPreferenciaRepository zonaPreferenciaRepository,
                                 PreferenciaRepository preferenciaRepository,
                                 AuditoriaService auditoriaService) {
        this.zonaTuristicaRepository = zonaTuristicaRepository;
        this.zonaPreferenciaRepository = zonaPreferenciaRepository;
        this.preferenciaRepository = preferenciaRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<ZonaTuristica> listarActivas() {
        return zonaTuristicaRepository.findByEstado("Activa");
    }

    public List<ZonaTuristica> listarTodas() {
        return zonaTuristicaRepository.findAll();
    }

    /** Listado de mantenimiento con estacion y preferencias ya cargadas (RNF-01). */
    public List<ZonaTuristica> listarTodasConEstacionYPreferencias() {
        return zonaTuristicaRepository.listarTodasConEstacionYPreferencias();
    }

    /** RF-09: zonas activas con estacion y preferencias ya cargadas, para el listado asignado. */
    public List<ZonaTuristica> listarActivasConEstacionYPreferencias() {
        return zonaTuristicaRepository.listarActivasConEstacionYPreferencias();
    }

    /** RF-03: solo zonas activas alcanzables desde la estacion elegida. */
    public List<ZonaTuristica> listarPorEstacion(Integer idEstacionCercana) {
        return zonaTuristicaRepository.findByEstacionCercana_IdAndEstado(idEstacionCercana, "Activa");
    }

    public Optional<ZonaTuristica> buscarPorId(Integer id) {
        return zonaTuristicaRepository.findById(id);
    }

    /** Zona con su estacion cercana resuelta, para el formulario de edicion. */
    public Optional<ZonaTuristica> buscarParaEdicion(Integer id) {
        return zonaTuristicaRepository.buscarConEstacion(id);
    }

    /**
     * CU-04: valida que la zona tenga al menos una preferencia asociada
     * (CN-05) y una estacion cercana, guarda el registro y deja constancia en
     * la auditoria indicando si fue alta o modificacion.
     */
    @Transactional
    public ZonaTuristica registrarOActualizar(ZonaTuristica zona, List<Integer> idsPreferencia, String usuario) {
        if (idsPreferencia == null || idsPreferencia.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos una preferencia");
        }
        if (zona.getEstacionCercana() == null) {
            throw new IllegalArgumentException("Debe seleccionar la estación ferroviaria más cercana");
        }
        if (zona.getEstado() == null || zona.getEstado().isBlank()) {
            zona.setEstado("Activa");
        }

        boolean esAlta = zona.getId() == null;
        String valorAnterior = esAlta ? null : describir(zonaTuristicaRepository.findById(zona.getId()).orElse(null));

        ZonaTuristica guardada = zonaTuristicaRepository.save(zona);
        asignarPreferencias(guardada, idsPreferencia);

        auditoriaService.registrarAuditoria(usuario, esAlta ? "INSERT" : "UPDATE", TABLA_AUDITADA,
                valorAnterior, describir(guardada) + "; preferencias=" + nombresDePreferencias(idsPreferencia));

        return guardada;
    }

    /**
     * RF-10: baja logica de la zona (ZonEstado = Inactiva). No se borra la
     * fila porque ruta_peatonal, control_aforo e informe_planificacion la
     * referencian con ON DELETE RESTRICT y el historial debe conservarse
     * (seccion 6.3).
     */
    @Transactional
    public void eliminar(Integer idZona, String usuario) {
        ZonaTuristica zona = zonaTuristicaRepository.findById(idZona)
                .orElseThrow(() -> new IllegalArgumentException("Zona turística no encontrada: " + idZona));

        String valorAnterior = describir(zona);
        zona.setEstado("Inactiva");
        zonaTuristicaRepository.save(zona);

        auditoriaService.registrarAuditoria(usuario, "DELETE", TABLA_AUDITADA,
                valorAnterior, describir(zona));
    }

    @Transactional
    public void asignarPreferencias(ZonaTuristica zona, List<Integer> idsPreferencia) {
        zonaPreferenciaRepository.deleteAll(
                zonaPreferenciaRepository.findByZonaTuristica_Id(zona.getId()));

        for (Integer idPreferencia : idsPreferencia) {
            Preferencia preferencia = preferenciaRepository.findById(idPreferencia)
                    .orElseThrow(() -> new IllegalArgumentException("Preferencia no encontrada: " + idPreferencia));
            ZonaPreferencia relacion = new ZonaPreferencia();
            relacion.setZonaTuristica(zona);
            relacion.setPreferencia(preferencia);
            zonaPreferenciaRepository.save(relacion);
        }
    }

    /** Ids de Preferencia ya asociados a la zona, para preseleccionarlos en el formulario. */
    public List<Integer> listarIdsPreferencia(Integer idZona) {
        return zonaPreferenciaRepository.findByZonaTuristica_Id(idZona).stream()
                .map(relacion -> relacion.getPreferencia().getId())
                .collect(Collectors.toList());
    }

    private String nombresDePreferencias(List<Integer> idsPreferencia) {
        return preferenciaRepository.findAllById(idsPreferencia).stream()
                .map(Preferencia::getNombre)
                .collect(Collectors.joining(", "));
    }

    /** Resumen de la zona en texto, acotado a los 500 caracteres de AudValorAnterior/Nuevo. */
    private String describir(ZonaTuristica zona) {
        if (zona == null) {
            return null;
        }
        String descripcion = "ZonNombre=" + zona.getNombre()
                + "; ZonIdEstacionCercana=" + (zona.getEstacionCercana() == null
                        ? "-" : zona.getEstacionCercana().getId())
                + "; ZonCostoAprox=" + zona.getCostoAprox()
                + "; ZonCupoMaximoDiario=" + zona.getCupoMaximoDiario()
                + "; ZonEstado=" + zona.getEstado();
        return descripcion.length() > 500 ? descripcion.substring(0, 500) : descripcion;
    }
}
