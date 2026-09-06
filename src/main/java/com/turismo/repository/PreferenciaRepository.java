package com.turismo.repository;

import com.turismo.model.Preferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/** RNF-06: tabla parametrica; las preferencias se leen de la BD, nunca del codigo. */
public interface PreferenciaRepository extends JpaRepository<Preferencia, Integer> {

    List<Preferencia> findAllByOrderByNombreAsc();

    Optional<Preferencia> findByNombre(String nombre);
}
