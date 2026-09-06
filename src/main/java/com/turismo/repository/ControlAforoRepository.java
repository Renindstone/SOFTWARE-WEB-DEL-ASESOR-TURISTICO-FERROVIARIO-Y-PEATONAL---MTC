package com.turismo.repository;

import com.turismo.model.ControlAforo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface ControlAforoRepository extends JpaRepository<ControlAforo, Integer> {

    Optional<ControlAforo> findByZona_IdAndFecha(Integer idZona, LocalDate fecha);

    /**
     * RNF-08: incremento atomico del contador de cupos. El limite se verifica
     * dentro del mismo UPDATE, de modo que dos grupos que confirman a la vez
     * la misma zona y fecha no puedan pasar ambos por encima del maximo.
     * Devuelve 1 si el incremento se aplico y 0 si ya no queda sitio.
     *
     * El aforo cuenta PERSONAS, no informes: una familia de tres descuenta
     * tres cupos, y o entran los tres o no entra ninguno -- de ahi que la
     * condicion sea cupoUtilizado + personas <= maximo y no un simple menor
     * que, que dejaria pasar grupos partidos por la mitad.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ControlAforo c
               SET c.cupoUtilizado = c.cupoUtilizado + :personas
             WHERE c.zona.id = :idZona
               AND c.fecha = :fecha
               AND c.cupoUtilizado + :personas <= :cupoMaximo
            """)
    int incrementarCupoUtilizado(@Param("idZona") Integer idZona,
                                 @Param("fecha") LocalDate fecha,
                                 @Param("personas") Integer personas,
                                 @Param("cupoMaximo") Integer cupoMaximo);
}
