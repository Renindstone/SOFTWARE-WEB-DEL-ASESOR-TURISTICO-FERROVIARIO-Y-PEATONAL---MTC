package com.turismo.repository;

import com.turismo.model.CategoriaVisitante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoriaVisitanteRepository extends JpaRepository<CategoriaVisitante, Integer> {

    /** Tramos de edad de un ambito ('Tren' o 'Zona'), del mas joven al mayor. */
    List<CategoriaVisitante> findByAmbitoOrderByEdadMinimaAsc(String ambito);
}
