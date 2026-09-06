package com.turismo.repository;

import com.turismo.model.InformeVisitante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InformeVisitanteRepository extends JpaRepository<InformeVisitante, Integer> {

    List<InformeVisitante> findByInforme_Id(Integer idInforme);
}
