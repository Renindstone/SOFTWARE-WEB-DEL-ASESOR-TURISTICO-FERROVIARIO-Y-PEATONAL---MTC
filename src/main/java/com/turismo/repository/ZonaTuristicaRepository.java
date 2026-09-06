package com.turismo.repository;

import com.turismo.model.ZonaTuristica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ZonaTuristicaRepository extends JpaRepository<ZonaTuristica, Integer> {

    List<ZonaTuristica> findByEstacionCercana_Id(Integer estIdEstacion);

    List<ZonaTuristica> findByEstacionCercana_IdAndEstado(Integer estIdEstacion, String estado);

    List<ZonaTuristica> findByEstado(String estado);

    /**
     * RF-09: zonas activas con su estacion y sus preferencias ya cargadas, para el
     * listado de estaciones con zonas asignadas de Travel Group Peru. El
     * fetch join evita una consulta por zona (RNF-01).
     */
    @Query("""
            SELECT DISTINCT z FROM ZonaTuristica z
              JOIN FETCH z.estacionCercana e
              LEFT JOIN FETCH z.preferencias zt
              LEFT JOIN FETCH zt.preferencia
             WHERE z.estado = 'Activa'
             ORDER BY e.nombre, z.nombre
            """)
    List<ZonaTuristica> listarActivasConEstacionYPreferencias();

    /**
     * Listado completo (activas e inactivas) para el panel de mantenimiento
     * de Travel Group Peru, con estacion y preferencias ya cargadas para evitar el
     * N+1 al pintar la tabla (RNF-01).
     */
    @Query("""
            SELECT DISTINCT z FROM ZonaTuristica z
              JOIN FETCH z.estacionCercana e
              LEFT JOIN FETCH z.preferencias zt
              LEFT JOIN FETCH zt.preferencia
             ORDER BY e.nombre, z.nombre
            """)
    List<ZonaTuristica> listarTodasConEstacionYPreferencias();

    /** Una zona con su estacion resuelta, para pintar el formulario de edicion. */
    @Query("""
            SELECT z FROM ZonaTuristica z
              JOIN FETCH z.estacionCercana
             WHERE z.id = :id
            """)
    java.util.Optional<ZonaTuristica> buscarConEstacion(@Param("id") Integer id);
}
