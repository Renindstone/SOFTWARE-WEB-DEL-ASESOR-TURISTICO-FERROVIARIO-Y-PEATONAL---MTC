package com.turismo.repository;

import com.turismo.model.ZonaPreferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ZonaPreferenciaRepository extends JpaRepository<ZonaPreferencia, Integer> {
    List<ZonaPreferencia> findByZonaTuristica_Id(Integer zonaId);
    List<ZonaPreferencia> findByPreferencia_Id(Integer preferenciaId);
}
