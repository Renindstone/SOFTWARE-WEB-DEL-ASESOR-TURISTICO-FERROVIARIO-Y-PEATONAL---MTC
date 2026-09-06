package com.turismo.service;

import com.turismo.dto.BusquedaZonaDTO;
import com.turismo.dto.ZonaResultadoDTO;
import com.turismo.model.Dificultad;
import com.turismo.model.Estacion;
import com.turismo.model.Preferencia;
import com.turismo.model.ZonaPreferencia;
import com.turismo.model.ZonaTuristica;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RF-03 (CN-01/CN-10): filtrado de zonas turisticas segun las preferencias
 * del turista. Los cuatro criterios son opcionales y se acumulan: sin
 * ninguno se devuelve el catalogo completo y cada filtro lo recorta.
 *
 * Se apoya en el motor de rutas real (RutaPeatonalService) para que tiempo y
 * dificultad se evaluen sobre el circuito efectivamente calculado, no sobre
 * valores fijos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenciaServiceTest {

    private static final Integer ID_OLLANTAYTAMBO = 4;
    private static final Integer ID_AGUAS_CALIENTES = 5;
    private static final Preferencia HISTORIA = crearPreferencia(1, "Historia/Cultura");
    private static final Preferencia NATURALEZA = crearPreferencia(2, "Naturaleza");
    private static final Preferencia AVENTURA = crearPreferencia(3, "Aventura");

    /** Mismos valores que siembra 02_datos.sql en la tabla dificultad. */
    private static final List<Dificultad> NIVELES = List.of(
            crearDificultad(1, "Baja", (short) 1, "3.00", (short) 12),
            crearDificultad(2, "Media", (short) 2, "6.00", (short) 18),
            crearDificultad(3, "Alta", (short) 3, null, (short) 25));

    @Mock
    private ZonaTuristicaService zonaTuristicaService;
    @Mock
    private com.turismo.repository.RutaPeatonalRepository rutaPeatonalRepository;
    @Mock
    private com.turismo.repository.DificultadRepository dificultadRepository;
    @Mock
    private EstacionService estacionService;

    private PreferenciaService preferenciaService;
    private Estacion ollantaytambo;
    private Estacion aguasCalientes;
    private ZonaTuristica fortaleza;
    private ZonaTuristica llaqta;
    private ZonaTuristica termales;

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

    private static Preferencia crearPreferencia(Integer id, String nombre) {
        Preferencia preferencia = new Preferencia();
        preferencia.setId(id);
        preferencia.setNombre(nombre);
        return preferencia;
    }

    private static Estacion crearEstacion(Integer id, String nombre, String lat, String lon) {
        Estacion estacion = new Estacion();
        estacion.setId(id);
        estacion.setNombre(nombre);
        estacion.setLatitud(new BigDecimal(lat));
        estacion.setLongitud(new BigDecimal(lon));
        estacion.setEstado("Activa");
        return estacion;
    }

    private ZonaTuristica crearZona(Integer id, String nombre, String lat, String lon,
                                     Estacion estacionCercana, Preferencia... preferencias) {
        ZonaTuristica zona = new ZonaTuristica();
        zona.setId(id);
        zona.setNombre(nombre);
        zona.setLatitud(new BigDecimal(lat));
        zona.setLongitud(new BigDecimal(lon));
        zona.setEstacionCercana(estacionCercana);
        zona.setEstado("Activa");
        zona.setPreferencias(Arrays.stream(preferencias).map(pref -> {
            ZonaPreferencia rel = new ZonaPreferencia();
            rel.setZonaTuristica(zona);
            rel.setPreferencia(pref);
            return rel;
        }).collect(java.util.stream.Collectors.toList()));
        return zona;
    }

    private BusquedaZonaDTO busqueda(Integer idEstacion, List<Integer> idsPreferencia,
                                        Integer minutos, String dificultad) {
        BusquedaZonaDTO dto = new BusquedaZonaDTO();
        dto.setIdEstacionOrigen(idEstacion);
        dto.setIdsPreferencia(idsPreferencia);
        dto.setTiempoDisponibleMin(minutos);
        dto.setDificultad(dificultad);
        return dto;
    }

    @BeforeEach
    void prepararEscenario() {
        preferenciaService = new PreferenciaService(zonaTuristicaService,
                new RutaPeatonalService(rutaPeatonalRepository, dificultadRepository),
                estacionService, dificultadRepository);

        // RNF-06: los umbrales y el ritmo de caminata salen de la tabla.
        when(dificultadRepository.findAllByOrderByOrdenAsc()).thenReturn(NIVELES);
        NIVELES.forEach(nivel ->
                when(dificultadRepository.findByNombre(nivel.getNombre())).thenReturn(Optional.of(nivel)));

        ollantaytambo = crearEstacion(ID_OLLANTAYTAMBO, "Estacion Ollantaytambo",
                "-13.258600", "-72.265000");
        aguasCalientes = crearEstacion(ID_AGUAS_CALIENTES, "Estacion Aguas Calientes",
                "-13.154700", "-72.525000");

        // 0.60 km de ida -> 1.20 km de circuito, 14 min, dificultad Baja.
        // CN-10: la fortaleza combina Historia/Cultura y Naturaleza a la vez.
        fortaleza = crearZona(6, "Conjunto Arqueologico de Ollantaytambo",
                "-13.254466", "-72.268563", ollantaytambo, HISTORIA, NATURALEZA);
        // 5.00 km de ida -> 10.00 km de circuito, 250 min, dificultad Alta.
        llaqta = crearZona(8, "Llaqta de Machu Picchu",
                "-13.193641", "-72.548093", aguasCalientes, AVENTURA);
        // 0.80 km de ida -> 1.60 km de circuito, 19 min, dificultad Baja.
        termales = crearZona(9, "Banos Termales de Aguas Calientes",
                "-13.152000", "-72.518300", aguasCalientes, NATURALEZA);

        when(estacionService.buscarActivaPorId(ID_OLLANTAYTAMBO)).thenReturn(ollantaytambo);
        when(estacionService.buscarActivaPorId(ID_AGUAS_CALIENTES)).thenReturn(aguasCalientes);
        when(zonaTuristicaService.listarActivasConEstacionYPreferencias())
                .thenReturn(List.of(fortaleza, llaqta, termales));
    }

    /** Sin ningun filtro se devuelve el catalogo completo de zonas activas. */
    @Test
    void devuelveTodasLasZonasCuandoNoSeDeclaraNingunFiltro() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, null));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactlyInAnyOrder(
                        "Conjunto Arqueologico de Ollantaytambo",
                        "Llaqta de Machu Picchu",
                        "Banos Termales de Aguas Calientes");
    }

    /**
     * Sin estacion declarada, cada zona se mide desde su propia estacion de
     * acceso: si no, no habria desde donde calcular el circuito.
     */
    @Test
    void mideCadaZonaDesdeSuEstacionDeAccesoCuandoNoSeEligeEstacion() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, null));

        assertThat(resultado)
                .extracting(ZonaResultadoDTO::getNombre, ZonaResultadoDTO::getNombreEstacionCercana)
                .contains(
                        org.assertj.core.api.Assertions.tuple(
                                "Conjunto Arqueologico de Ollantaytambo", "Estacion Ollantaytambo"),
                        org.assertj.core.api.Assertions.tuple(
                                "Llaqta de Machu Picchu", "Estacion Aguas Calientes"));
    }

    /** RF-02: al declarar estacion solo quedan las zonas que dependen de ella. */
    @Test
    void recortaPorEstacionDePartida() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_AGUAS_CALIENTES, null, null, null));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactlyInAnyOrder(
                        "Llaqta de Machu Picchu", "Banos Termales de Aguas Calientes");
    }

    /** Cada filtro que se anade recorta lo que dejo el anterior. */
    @Test
    void cadaFiltroAnadidoRecortaElListado() {
        assertThat(preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, null))).hasSize(3);
        assertThat(preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_AGUAS_CALIENTES, null, null, null))).hasSize(2);
        assertThat(preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_AGUAS_CALIENTES, null, null, "Baja"))).hasSize(1);
        assertThat(preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_AGUAS_CALIENTES, List.of(AVENTURA.getId()), null, "Baja"))).isEmpty();
    }

    /** CN-10: una zona con dos categorias aparece buscando por cualquiera de ellas. */
    @Test
    void devuelveLaZonaTantoPorNaturalezaComoPorHistoriaCultura() {
        List<ZonaResultadoDTO> porNaturaleza = preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_OLLANTAYTAMBO, List.of(NATURALEZA.getId()), 600, "Alta"));
        List<ZonaResultadoDTO> porHistoria = preferenciaService.buscarZonasRecomendadas(
                busqueda(ID_OLLANTAYTAMBO, List.of(HISTORIA.getId()), 600, "Alta"));

        assertThat(porNaturaleza).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
        assertThat(porHistoria).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
    }

    /** RF-03: el circuito debe caber en el tiempo declarado por el turista. */
    @Test
    void descartaLasZonasQueNoCabenEnElTiempoDisponible() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, 30, null));

        // Fortaleza 14 min y termales 19 min entran; la llaqta, 250 min, no.
        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactlyInAnyOrder(
                        "Conjunto Arqueologico de Ollantaytambo",
                        "Banos Termales de Aguas Calientes");
    }

    /** La dificultad elegida es un techo: "Baja" excluye las rutas exigentes. */
    @Test
    void descartaLasZonasQueSuperanLaDificultadAceptada() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, "Baja"));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactlyInAnyOrder(
                        "Conjunto Arqueologico de Ollantaytambo",
                        "Banos Termales de Aguas Calientes");
    }

    /** CN-02: una zona cuya estacion de acceso esta de baja no es alcanzable. */
    @Test
    void omiteLasZonasCuyaEstacionDeAccesoEstaInactiva() {
        aguasCalientes.setEstado("Inactiva");

        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, null));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
    }

    /** Cada resultado llega con su ruta ya calculada, para no recalcularla en la vista. */
    @Test
    void adjuntaLaRutaCalculadaACadaResultado() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                busqueda(null, null, null, null));

        assertThat(resultado).hasSize(3);
        assertThat(resultado.get(0).getRuta().getDistanciaKm()).isEqualByComparingTo("1.20");
        assertThat(resultado.get(0).getRuta().getEsIdaVuelta()).isTrue();
        // Ordenado por distancia: primero la ruta mas corta.
        assertThat(resultado).extracting(zona -> zona.getRuta().getDistanciaKm())
                .isSortedAccordingTo(java.util.Comparator.naturalOrder());
    }
}
