package com.turismo.repository;

import com.turismo.model.ServicioTren;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Las vistas muestran el nombre de las estaciones de origen y destino, y la
 * aplicacion corre con open-in-view desactivado (RNF-01): las consultas que
 * alimentan una vista traen ambas asociaciones con JOIN FETCH.
 */
public interface ServicioTrenRepository extends JpaRepository<ServicioTren, Integer> {

    List<ServicioTren> findByEstacionOrigen_Id(Integer estIdEstacion);

    /** RF-13: listado completo del panel de horarios y tarifas. */
    @Query("""
            SELECT s FROM ServicioTren s
              JOIN FETCH s.estacionOrigen o
              JOIN FETCH s.estacionDestino d
             ORDER BY o.nombre, s.horarioSalida
            """)
    List<ServicioTren> listarConEstaciones();

    /** RF-07: servicios que llegan a la estacion de partida, del mas barato al mas caro. */
    @Query("""
            SELECT s FROM ServicioTren s
              JOIN FETCH s.estacionOrigen o
              JOIN FETCH s.estacionDestino d
             WHERE d.id = :idEstacionDestino
               AND o.estado = 'Activa'
             ORDER BY s.tarifa ASC
            """)
    List<ServicioTren> listarHaciaEstacion(@Param("idEstacionDestino") Integer idEstacionDestino);

    /** Un servicio con sus estaciones resueltas, para editarlo en el formulario. */
    @Query("""
            SELECT s FROM ServicioTren s
              JOIN FETCH s.estacionOrigen
              JOIN FETCH s.estacionDestino
             WHERE s.id = :id
            """)
    java.util.Optional<ServicioTren> buscarConEstaciones(@Param("id") Integer id);

    /**
     * Clave natural del feed de PeruRail (tramo + hora de salida). La usa la
     * sincronizacion para reconocer un servicio ya cargado en vez de volver a
     * insertarlo, y se corresponde con uq_servicio_tramo_hora.
     */
    Optional<ServicioTren> findByEstacionOrigen_IdAndEstacionDestino_IdAndHorarioSalida(
            Integer idEstacionOrigen, Integer idEstacionDestino, java.time.LocalTime horarioSalida);

}
