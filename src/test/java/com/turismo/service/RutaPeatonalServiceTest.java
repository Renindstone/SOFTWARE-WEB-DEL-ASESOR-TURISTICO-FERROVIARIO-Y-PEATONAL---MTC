package com.turismo.service;

import com.turismo.dto.RutaCalculadaDTO;
import com.turismo.exception.EstacionInactivaException;
import com.turismo.exception.RutaInvalidaException;
import com.turismo.model.Dificultad;
import com.turismo.model.Estacion;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.DificultadRepository;
import com.turismo.repository.RutaPeatonalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Caja Blanca: CB-01 (calcularRutaPeatonalIdaVuelta - ruta valida) y
 * CB-02 (calcularRutaPeatonalIdaVuelta - distancia cero -> excepcion).
 *
 * La distancia se mide entre la estacion de origen y las coordenadas
 * propias de la zona turistica (seccion 5.1). Medirla contra la estacion
 * cercana de la zona daria cero siempre que el turista parta justamente de
 * esa estacion, que es el flujo normal del CU-02.
 *
 * Los umbrales y el ritmo de caminata llegan de la tabla dificultad
 * (RNF-06); la prueba reproduce los valores que siembra 02_datos.sql.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RutaPeatonalServiceTest {

    private static final List<Dificultad> NIVELES = List.of(
            crearDificultad(1, "Baja", (short) 1, "3.00", (short) 12),
            crearDificultad(2, "Media", (short) 2, "6.00", (short) 18),
            crearDificultad(3, "Alta", (short) 3, null, (short) 25));

    @Mock
    private RutaPeatonalRepository rutaPeatonalRepository;
    @Mock
    private DificultadRepository dificultadRepository;

    private RutaPeatonalService rutaPeatonalService;

    private static Dificultad crearDificultad(Integer id, String nombre, Short orden,
                                               String distanciaMaximaKm, Short velocidad) {
        Dificultad dificultad = new Dificultad();
        dificultad.setId(id);
        dificultad.setNombre(nombre);
        dificultad.setOrden(orden);
        dificultad.setDistanciaMaximaKm(distanciaMaximaKm == null ? null : new BigDecimal(distanciaMaximaKm));
        dificultad.setVelocidadMinPorKm(velocidad);
        return dificultad;
    }

    @BeforeEach
    void prepararNiveles() {
        rutaPeatonalService = new RutaPeatonalService(rutaPeatonalRepository, dificultadRepository);
        when(dificultadRepository.findAllByOrderByOrdenAsc()).thenReturn(NIVELES);
    }

    private Estacion crearEstacion(String lat, String lon) {
        Estacion estacion = new Estacion();
        estacion.setId(3);
        estacion.setNombre("Ollantaytambo");
        estacion.setLatitud(new BigDecimal(lat));
        estacion.setLongitud(new BigDecimal(lon));
        estacion.setEstado("Activa");
        return estacion;
    }

    private ZonaTuristica crearZona(String lat, String lon) {
        ZonaTuristica zona = new ZonaTuristica();
        zona.setId(1);
        zona.setNombre("Fortaleza de Ollantaytambo");
        zona.setLatitud(new BigDecimal(lat));
        zona.setLongitud(new BigDecimal(lon));
        return zona;
    }

    /** CB-01: coordenadas validas y distancia de ida > 0 -> circuito duplicado. */
    @Test
    void cb01_calculaCircuitoIdaVueltaCuandoLaDistanciaEsMayorACero() {
        // Estación Ollantaytambo y la fortaleza, separadas por 0.60 km de ida.
        Estacion origen = crearEstacion("-13.258600", "-72.265000");
        ZonaTuristica destino = crearZona("-13.254466", "-72.268563");

        RutaCalculadaDTO ruta = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(origen, destino);

        assertThat(ruta.getEsIdaVuelta()).isTrue();
        assertThat(ruta.getDistanciaKm()).isEqualByComparingTo(new BigDecimal("1.20"));
        // 1.20 km al ritmo de 12 min/km que la tabla asocia a "Baja".
        assertThat(ruta.getTiempoEstimadoMin()).isEqualTo(14);
        assertThat(ruta.getDificultad()).isEqualTo("Baja");
        assertThat(ruta.getOrdenDificultad()).isEqualTo((short) 1);
    }

    /** CB-02: la zona coincide con la estacion -> distancia de ida cero. */
    @Test
    void cb02_lanzaExcepcionCuandoLaDistanciaDeIdaEsCero() {
        Estacion origen = crearEstacion("-13.154700", "-72.525000");
        ZonaTuristica destino = crearZona("-13.154700", "-72.525000");

        assertThatThrownBy(() -> rutaPeatonalService.calcularRutaPeatonalIdaVuelta(origen, destino))
                .isInstanceOf(RutaInvalidaException.class)
                .hasMessageContaining("distancia caminable mayor a cero");
    }

    /** La dificultad sube con la distancia del circuito completo (RF-05). */
    @Test
    void clasificaLaDificultadSegunLaDistanciaDelCircuito() {
        Estacion origen = crearEstacion("-13.154700", "-72.525000");

        // 5.00 km de ida -> 10.00 km de circuito -> sin tope: nivel mas exigente.
        RutaCalculadaDTO larga = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(
                origen, crearZona("-13.193641", "-72.548093"));
        assertThat(larga.getDificultad()).isEqualTo("Alta");
        // 10.00 km al ritmo de 25 min/km que la tabla asocia a "Alta".
        assertThat(larga.getTiempoEstimadoMin()).isEqualTo(250);

        // 1.90 km de ida -> 3.80 km de circuito -> franja media (3.00 < d <= 6.00).
        RutaCalculadaDTO media = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(
                origen, crearZona("-13.156189", "-72.542481"));
        assertThat(media.getDificultad()).isEqualTo("Media");
        assertThat(media.getTiempoEstimadoMin()).isEqualTo(68);
    }

    /**
     * RNF-06: el ritmo de caminata es un dato de la tabla, no una constante
     * del codigo. Cambiarlo en la BD debe cambiar el tiempo estimado.
     */
    @Test
    void tomaElRitmoDeCaminataDeLaTablaDificultad() {
        when(dificultadRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of(
                crearDificultad(1, "Baja", (short) 1, "3.00", (short) 20)));

        RutaCalculadaDTO ruta = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(
                crearEstacion("-13.258600", "-72.265000"),
                crearZona("-13.254466", "-72.268563"));

        // 1.20 km x 20 min/km = 24 min, frente a los 14 min del ritmo por defecto.
        assertThat(ruta.getTiempoEstimadoMin()).isEqualTo(24);
    }

    /** Sin niveles configurados el motor no puede clasificar la ruta. */
    @Test
    void fallaCuandoNoHayNivelesDeDificultadConfigurados() {
        when(dificultadRepository.findAllByOrderByOrdenAsc()).thenReturn(List.of());

        assertThatThrownBy(() -> rutaPeatonalService.calcularRutaPeatonalIdaVuelta(
                crearEstacion("-13.258600", "-72.265000"),
                crearZona("-13.254466", "-72.268563")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dificultad");
    }

    /** RF-02 (CN-02): una estacion inactiva no puede ser punto de partida. */
    @Test
    void rechazaLaEstacionDeOrigenInactiva() {
        Estacion origen = crearEstacion("-13.474400", "-72.042800");
        origen.setNombre("Estacion Poroy");
        origen.setEstado("Inactiva");

        assertThatThrownBy(() -> rutaPeatonalService.calcularRutaPeatonalIdaVuelta(
                origen, crearZona("-13.254466", "-72.268563")))
                .isInstanceOf(EstacionInactivaException.class)
                .hasMessageContaining("inactiva");
    }
}
