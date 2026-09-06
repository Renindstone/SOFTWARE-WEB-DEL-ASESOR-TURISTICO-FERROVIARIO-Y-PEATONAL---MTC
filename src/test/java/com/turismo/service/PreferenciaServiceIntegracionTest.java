package com.turismo.service;

import com.turismo.dto.PreferenciaDTO;
import com.turismo.dto.ZonaResultadoDTO;
import com.turismo.model.Dificultad;
import com.turismo.model.Estacion;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.DificultadRepository;
import com.turismo.repository.EstacionRepository;
import com.turismo.repository.ZonaTuristicaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * El buscador de zonas descarta sobre la marcha las que no son alcanzables.
 * Con las transacciones reales de Spring por medio, descartar una zona no
 * puede costar la busqueda entera: cuando el motor de rutas abria su propia
 * transaccion, la excepcion de una sola zona marcaba la del buscador como
 * rollback-only y el listado completo acababa en un error 500, aunque el
 * buscador capturase esa excepcion.
 *
 * Las pruebas unitarias con dobles no ven ese fallo porque ahi no hay
 * transaccion; por eso esta se ejecuta sobre el contexto completo. Y a
 * proposito no lleva @Transactional: envolverla en la transaccion de la
 * prueba volveria a esconder el problema.
 */
@SpringBootTest
class PreferenciaServiceIntegracionTest {

    @Autowired
    private PreferenciaService preferenciaService;
    @Autowired
    private EstacionRepository estacionRepository;
    @Autowired
    private ZonaTuristicaRepository zonaTuristicaRepository;
    @Autowired
    private DificultadRepository dificultadRepository;

    private Estacion activa;
    private Estacion inactiva;

    private Estacion crearEstacion(String codigo, String nombre, String lat, String lon, String estado) {
        Estacion estacion = new Estacion();
        estacion.setCodigo(codigo);
        estacion.setNombre(nombre);
        estacion.setLatitud(new BigDecimal(lat));
        estacion.setLongitud(new BigDecimal(lon));
        estacion.setCiudad("Cusco");
        estacion.setEstado(estado);
        return estacionRepository.save(estacion);
    }

    private ZonaTuristica crearZona(String nombre, String lat, String lon, Estacion estacionCercana) {
        ZonaTuristica zona = new ZonaTuristica();
        zona.setNombre(nombre);
        zona.setLatitud(new BigDecimal(lat));
        zona.setLongitud(new BigDecimal(lon));
        zona.setEstacionCercana(estacionCercana);
        zona.setEstado("Activa");
        return zonaTuristicaRepository.save(zona);
    }

    private Dificultad crearDificultad(String nombre, short orden, String tope, short velocidad) {
        Dificultad dificultad = new Dificultad();
        dificultad.setNombre(nombre);
        dificultad.setOrden(orden);
        dificultad.setDistanciaMaximaKm(tope == null ? null : new BigDecimal(tope));
        dificultad.setVelocidadMinPorKm(velocidad);
        return dificultadRepository.save(dificultad);
    }

    @BeforeEach
    void sembrarEscenario() {
        zonaTuristicaRepository.deleteAll();
        estacionRepository.deleteAll();
        dificultadRepository.deleteAll();

        crearDificultad("Baja", (short) 1, "3.00", (short) 12);
        crearDificultad("Media", (short) 2, "6.00", (short) 18);
        crearDificultad("Alta", (short) 3, null, (short) 25);

        activa = crearEstacion("TST-ACT", "Estacion Activa", "-13.258600", "-72.265000", "Activa");
        inactiva = crearEstacion("TST-INA", "Estacion Inactiva", "-13.474400", "-72.042800", "Inactiva");

        crearZona("Zona alcanzable", "-13.254466", "-72.268563", activa);
        // CN-02: solo se llega por una estacion dada de baja.
        crearZona("Zona tras estacion inactiva", "-13.470000", "-72.040000", inactiva);
        // CB-02: comparte coordenadas con su estacion, no hay nada que caminar.
        crearZona("Zona sobre la propia estacion", "-13.258600", "-72.265000", activa);
    }

    @AfterEach
    void limpiar() {
        zonaTuristicaRepository.deleteAll();
        estacionRepository.deleteAll();
        dificultadRepository.deleteAll();
    }

    /**
     * Las dos zonas descartables no deben impedir que se devuelva la tercera:
     * este es el caso que producia el 500.
     */
    @Test
    void descartarZonasNoInvalidaLaBusquedaCompleta() {
        PreferenciaDTO sinFiltros = new PreferenciaDTO();

        assertThatCode(() -> preferenciaService.buscarZonasRecomendadas(sinFiltros))
                .doesNotThrowAnyException();

        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(sinFiltros);
        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Zona alcanzable");
        assertThat(resultado.get(0).getNombreEstacionCercana()).isEqualTo("Estacion Activa");
    }

    /** Y lo mismo filtrando por la estacion activa, que es el otro camino del metodo. */
    @Test
    void devuelveLaZonaAlcanzableAlFiltrarPorEstacion() {
        PreferenciaDTO preferencia = new PreferenciaDTO();
        preferencia.setIdEstacionOrigen(activa.getId());

        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(preferencia);

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Zona alcanzable");
    }
}
