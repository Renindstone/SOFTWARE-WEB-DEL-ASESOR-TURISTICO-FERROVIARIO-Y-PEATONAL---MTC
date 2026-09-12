package com.turismo.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La entidad ZonaTuristica replica en anotaciones las restricciones de la
 * tabla (NOT NULL, longitudes, CHECK de coordenadas, cupo y estado): asi el
 * formulario de administracion las anuncia con su mensaje en vez de dejar
 * que la base las rechace con un error inesperado (CN-04 / CN-05).
 */
class ZonaTuristicaTest {

    private static ValidatorFactory factory;
    private static Validator validador;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validador = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        if (factory != null) {
            factory.close();
        }
    }

    private ZonaTuristica valida() {
        Estacion estacion = new Estacion();
        estacion.setId(4);
        ZonaTuristica zona = new ZonaTuristica();
        zona.setNombre("Salinas de Maras");
        zona.setDescripcion("Pozas de sal en terrazas.");
        zona.setLatitud(new BigDecimal("-13.303000"));
        zona.setLongitud(new BigDecimal("-72.155000"));
        zona.setEstacionCercana(estacion);
        zona.setCostoAprox(new BigDecimal("10.00"));
        zona.setCupoMaximoDiario(500);
        zona.setEstado("Activa");
        return zona;
    }

    private Set<String> camposConError(ZonaTuristica zona) {
        return validador.validate(zona).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Una zona completa y coherente no produce violaciones")
    void zonaValida() {
        assertThat(camposConError(valida())).isEmpty();
    }

    @Test
    @DisplayName("Nombre vacío o de más de 100 caracteres: se rechaza")
    void nombre() {
        ZonaTuristica sinNombre = valida();
        sinNombre.setNombre("   ");
        assertThat(camposConError(sinNombre)).contains("nombre");

        ZonaTuristica largo = valida();
        largo.setNombre("Z".repeat(101));
        assertThat(camposConError(largo)).contains("nombre");
    }

    @Test
    @DisplayName("Coordenadas fuera del globo o ausentes: se rechazan")
    void coordenadas() {
        ZonaTuristica fuera = valida();
        fuera.setLatitud(new BigDecimal("999"));
        fuera.setLongitud(new BigDecimal("-181"));
        assertThat(camposConError(fuera)).contains("latitud", "longitud");

        ZonaTuristica sinLongitud = valida();
        sinLongitud.setLongitud(null);
        assertThat(camposConError(sinLongitud)).contains("longitud");
    }

    @Test
    @DisplayName("Costo negativo o con más de 5 enteros, cupo cero, estado inventado: se rechazan")
    void costoCupoEstado() {
        ZonaTuristica costo = valida();
        costo.setCostoAprox(new BigDecimal("-10"));
        assertThat(camposConError(costo)).contains("costoAprox");

        ZonaTuristica costoEnorme = valida();
        costoEnorme.setCostoAprox(new BigDecimal("99999999"));
        assertThat(camposConError(costoEnorme)).contains("costoAprox");

        ZonaTuristica cupo = valida();
        cupo.setCupoMaximoDiario(0);
        assertThat(camposConError(cupo)).contains("cupoMaximoDiario");

        ZonaTuristica estado = valida();
        estado.setEstado("Hackeada");
        assertThat(camposConError(estado)).contains("estado");
    }

    @Test
    @DisplayName("Sin estación cercana: se rechaza")
    void estacion() {
        ZonaTuristica zona = valida();
        zona.setEstacionCercana(null);
        assertThat(camposConError(zona)).contains("estacionCercana");
    }
}
