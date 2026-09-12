package com.turismo.integration.senamhi.mock;

import com.turismo.integration.senamhi.dto.PrevisionClimaSenamhiDTO;
import com.turismo.model.Estacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mock del SENAMHI (Sprint 0). El pronostico generado tiene que respetar las
 * mismas restricciones que la tabla prevision_clima, porque entra al sistema
 * por el mismo camino que entraria el feed real (RF-15, CB-03/CB-04).
 */
class GeneradorPronosticoSenamhiTest {

    private final GeneradorPronosticoSenamhi generador = new GeneradorPronosticoSenamhi();

    private Estacion crearEstacion(String codigo, String altitud) {
        Estacion estacion = new Estacion();
        estacion.setCodigo(codigo);
        estacion.setNombre("Estacion " + codigo);
        estacion.setAltitud(altitud == null ? null : new BigDecimal(altitud));
        return estacion;
    }

    /** La sincronizacion diaria puede repetirse sin que el clima cambie. */
    @Test
    void generaSiempreElMismoPronosticoParaLaMismaEstacionYFecha() {
        Estacion estacion = crearEstacion("CUS-OLL", "2792.00");
        LocalDate fecha = LocalDate.of(2026, 9, 15);

        PrevisionClimaSenamhiDTO primera = generador.generar(estacion, fecha);
        PrevisionClimaSenamhiDTO segunda = generador.generar(estacion, fecha);

        assertThat(primera.getTemperaturaMinimaC()).isEqualByComparingTo(segunda.getTemperaturaMinimaC());
        assertThat(primera.getTemperaturaMaximaC()).isEqualByComparingTo(segunda.getTemperaturaMaximaC());
        assertThat(primera.getProbabilidadLluvia()).isEqualByComparingTo(segunda.getProbabilidadLluvia());
        assertThat(primera.getEstadoClima()).isEqualTo(segunda.getEstadoClima());
    }

    /** Dos dias distintos no pueden devolver exactamente el mismo parte. */
    @Test
    void variaElPronosticoEntreDiasDistintos() {
        Estacion estacion = crearEstacion("CUS-OLL", "2792.00");

        PrevisionClimaSenamhiDTO hoy = generador.generar(estacion, LocalDate.of(2026, 9, 15));
        PrevisionClimaSenamhiDTO manana = generador.generar(estacion, LocalDate.of(2026, 9, 16));

        assertThat(hoy.getProbabilidadLluvia()).isNotEqualByComparingTo(manana.getProbabilidadLluvia());
    }

    /**
     * ck_clima_lluvia (BETWEEN 0 AND 100), ck_clima_temp_rango (maxima >=
     * minima) y NUMERIC(4,1) en las tres columnas: se recorre un ano completo
     * de todas las estaciones sembradas para que ningun dia viole el esquema.
     */
    @Test
    void respetaLasRestriccionesDeLaTablaPrevisionClimaTodoElAno() {
        Estacion[] estaciones = {
                crearEstacion("CUS-SPD", "3399.00"),
                crearEstacion("CUS-MAP", "2040.00"),
                crearEstacion("PUN-PUN", "3827.00"),
                crearEstacion("AQP-AQP", "2335.00"),
                crearEstacion("SIN-ALT", null)
        };
        LocalDate fecha = LocalDate.of(2026, 1, 1);

        for (int dia = 0; dia < 365; dia++) {
            for (Estacion estacion : estaciones) {
                PrevisionClimaSenamhiDTO prevision = generador.generar(estacion, fecha.plusDays(dia));

                assertThat(prevision.getProbabilidadLluvia())
                        .isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
                assertThat(prevision.getTemperaturaMaximaC())
                        .isGreaterThanOrEqualTo(prevision.getTemperaturaMinimaC());
                assertThat(prevision.getTemperaturaMinimaC().scale()).isLessThanOrEqualTo(1);
                assertThat(prevision.getTemperaturaMaximaC().scale()).isLessThanOrEqualTo(1);
                assertThat(prevision.getProbabilidadLluvia().scale()).isLessThanOrEqualTo(1);
                assertThat(prevision.getEstadoClima()).isNotBlank().hasSizeLessThanOrEqualTo(30);
                assertThat(prevision.getCodigoEstacion()).isEqualTo(estacion.getCodigo());
            }
        }
    }

    /** A mayor altitud, temperaturas mas bajas: Puno debe ser mas frio que Aguas Calientes. */
    @Test
    void bajaLaTemperaturaConLaAltitud() {
        LocalDate fecha = LocalDate.of(2026, 7, 20);

        PrevisionClimaSenamhiDTO aguasCalientes = generador.generar(crearEstacion("CUS-MAP", "2040.00"), fecha);
        PrevisionClimaSenamhiDTO puno = generador.generar(crearEstacion("PUN-PUN", "3827.00"), fecha);

        assertThat(puno.getTemperaturaMaximaC()).isLessThan(aguasCalientes.getTemperaturaMaximaC());
    }

    /**
     * En la sierra sur llueve mucho mas en enero que en julio. Se promedia
     * sobre varios dias para que el ruido diario no decida el resultado.
     */
    @Test
    void llueveMasEnTemporadaHumedaQueEnTemporadaSeca() {
        Estacion estacion = crearEstacion("CUS-SPD", "3399.00");

        double enero = promedioLluvia(estacion, LocalDate.of(2026, 1, 10), 15);
        double julio = promedioLluvia(estacion, LocalDate.of(2026, 7, 10), 15);

        assertThat(enero).isGreaterThan(julio + 30);
    }

    private double promedioLluvia(Estacion estacion, LocalDate inicio, int dias) {
        double total = 0;
        for (int dia = 0; dia < dias; dia++) {
            total += generador.generar(estacion, inicio.plusDays(dia)).getProbabilidadLluvia().doubleValue();
        }
        return total / dias;
    }
}
