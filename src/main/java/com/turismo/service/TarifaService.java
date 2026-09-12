package com.turismo.service;

import com.turismo.exception.TarifaInvalidaException;
import com.turismo.model.CategoriaVisitante;
import com.turismo.repository.CategoriaVisitanteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * RF-19: resuelve que tarifa le corresponde a cada visitante segun su edad.
 *
 * Los cortes de edad los define la tabla parametrica categoria_visitante y no
 * este codigo, porque no son los mismos en los dos proveedores: PeruRail cobra
 * tarifa de adulto desde los 12 anos, mientras que la tarifa del santuario
 * mantiene la de nino hasta los 17. Por eso cada consulta lleva su ambito.
 */
@Service
public class TarifaService {

    /** Los importes en soles se redondean a dos decimales, como NUMERIC(7,2). */
    private static final int DECIMALES_SOLES = 2;

    private final CategoriaVisitanteRepository categoriaVisitanteRepository;

    public TarifaService(CategoriaVisitanteRepository categoriaVisitanteRepository) {
        this.categoriaVisitanteRepository = categoriaVisitanteRepository;
    }

    /**
     * Categoria tarifaria de una edad dentro de un ambito. La restriccion
     * EXCLUDE de la tabla garantiza que los tramos no se solapen, asi que
     * como mucho hay una categoria por edad.
     */
    @Transactional(readOnly = true)
    public CategoriaVisitante categoriaPorEdad(String ambito, int edad) {
        if (edad < 0) {
            throw new IllegalArgumentException("La edad no puede ser negativa: " + edad);
        }
        List<CategoriaVisitante> categorias =
                categoriaVisitanteRepository.findByAmbitoOrderByEdadMinimaAsc(ambito);

        return categorias.stream()
                .filter(categoria -> categoria.cubre(edad))
                .findFirst()
                .orElseThrow(() -> new TarifaInvalidaException(
                        "No hay tarifa configurada para la edad " + edad + " en el ámbito " + ambito));
    }

    /** Categorias de un ambito, para poblar los selectores de la vista. */
    @Transactional(readOnly = true)
    public List<CategoriaVisitante> listarPorAmbito(String ambito) {
        return categoriaVisitanteRepository.findByAmbitoOrderByEdadMinimaAsc(ambito);
    }

    /**
     * Precio de una categoria sobre un precio base, multiplicado por el numero
     * de personas. Un precio base nulo (zona sin costo de ingreso, o informe
     * sin tren) se trata como cero.
     */
    public BigDecimal calcularSubtotal(BigDecimal precioBase, CategoriaVisitante categoria, int cantidad) {
        if (precioBase == null || categoria == null || cantidad <= 0) {
            return BigDecimal.ZERO.setScale(DECIMALES_SOLES, RoundingMode.HALF_UP);
        }
        return precioBase
                .multiply(categoria.getFactorPrecio())
                .multiply(BigDecimal.valueOf(cantidad))
                .setScale(DECIMALES_SOLES, RoundingMode.HALF_UP);
    }
}
