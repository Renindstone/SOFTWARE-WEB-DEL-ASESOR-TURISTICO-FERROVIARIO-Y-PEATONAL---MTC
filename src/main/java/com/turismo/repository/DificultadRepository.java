package com.turismo.repository;

import com.turismo.model.Dificultad;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** RNF-06: los parametros de dificultad se leen de la BD, no del codigo. */
public interface DificultadRepository extends JpaRepository<Dificultad, Integer> {

    Optional<Dificultad> findByNombre(String nombre);

    /** De la mas suave a la mas exigente, que es el orden en que se evalua. */
    List<Dificultad> findAllByOrderByOrdenAsc();
}
