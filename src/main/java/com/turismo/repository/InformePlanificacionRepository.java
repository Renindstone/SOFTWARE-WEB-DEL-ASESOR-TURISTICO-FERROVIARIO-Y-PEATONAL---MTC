package com.turismo.repository;

import com.turismo.model.InformePlanificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface InformePlanificacionRepository extends JpaRepository<InformePlanificacion, Integer> {

    Optional<InformePlanificacion> findByCodigo(String codigo);

    Optional<InformePlanificacion> findTopByOrderByIdDesc();

    /** Obtiene el historial de informes para un usuario especifico, ordenados de mas reciente a mas antiguo. */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"ruta", "ruta.zonaDestino", "ruta.estacionOrigen"})
    List<InformePlanificacion> findByUsuario_NombreUsuarioOrderByFechaEmisionDesc(String nombreUsuario);
}
