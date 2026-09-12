package com.turismo.service;

import com.turismo.exception.EstacionInactivaException;
import com.turismo.model.Estacion;
import com.turismo.repository.EstacionRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** RF-02/RF-12: catalogo de estaciones (lectura por Travel Group Perú y usuario final). */
@Service
public class EstacionService {

    private final EstacionRepository estacionRepository;

    public EstacionService(EstacionRepository estacionRepository) {
        this.estacionRepository = estacionRepository;
    }

    /** RF-02 (CN-02): excluye del listado las estaciones marcadas como inactivas. */
    public List<Estacion> listarActivas() {
        return estacionRepository.findByEstadoOrderByNombreAsc("Activa");
    }

    public List<Estacion> listarTodas() {
        return estacionRepository.findAllByOrderByNombreAsc();
    }

    public Optional<Estacion> buscarPorId(Integer id) {
        return estacionRepository.findById(id);
    }

    /**
     * RF-02: resuelve la estacion de partida rechazando las inactivas, para
     * que no puedan usarse llamando directamente a la URL sin pasar por el
     * selector del formulario.
     */
    public Estacion buscarActivaPorId(Integer id) {
        Estacion estacion = estacionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Estación no encontrada: " + id));
        if (!"Activa".equalsIgnoreCase(estacion.getEstado())) {
            throw new EstacionInactivaException(
                    "La estación " + estacion.getNombre() + " está inactiva y no puede usarse como punto de partida");
        }
        return estacion;
    }

    public Estacion guardar(Estacion estacion) {
        return estacionRepository.save(estacion);
    }
}
