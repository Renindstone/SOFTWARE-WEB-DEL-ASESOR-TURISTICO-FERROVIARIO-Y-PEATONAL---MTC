package com.turismo.service;

import com.turismo.dto.PreferenciaDTO;
import com.turismo.dto.ZonaResultadoDTO;
import com.turismo.model.Estacion;
import com.turismo.model.TipoTurismo;
import com.turismo.model.ZonaTipoTurismo;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.ZonaTipoTurismoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.turismo.model.Dificultad;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * RF-03 (CN-01/CN-10): filtrado de zonas turisticas segun las preferencias
 * del turista. Se apoya en el motor de rutas real (RutaPeatonalService) para
 * que tiempo y dificultad se evaluen sobre el circuito efectivamente
 * calculado, no sobre valores fijos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreferenciaServiceTest {

    private static final Integer ID_ESTACION = 4;
    private static final TipoTurismo HISTORIA = crearTipo(1, "Historia/Cultura");
    private static final TipoTurismo NATURALEZA = crearTipo(2, "Naturaleza");
    private static final TipoTurismo AVENTURA = crearTipo(3, "Aventura");

    /** Mismos valores que siembra 02_datos.sql en la tabla dificultad. */
    private static final List<Dificultad> NIVELES = List.of(
            crearDificultad(1, "Baja", (short) 1, "3.00", (short) 12),
            crearDificultad(2, "Media", (short) 2, "6.00", (short) 18),
            crearDificultad(3, "Alta", (short) 3, null, (short) 25));

    @Mock
    private ZonaTuristicaService zonaTuristicaService;
    @Mock
    private ZonaTipoTurismoRepository zonaTipoTurismoRepository;
    @Mock
    private com.turismo.repository.RutaPeatonalRepository rutaPeatonalRepository;
    @Mock
    private com.turismo.repository.DificultadRepository dificultadRepository;
    @Mock
    private EstacionService estacionService;

    private PreferenciaService preferenciaService;
    private Estacion origen;
    private ZonaTuristica fortaleza;
    private ZonaTuristica llaqta;

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

    private static TipoTurismo crearTipo(Integer id, String nombre) {
        TipoTurismo tipo = new TipoTurismo();
        tipo.setId(id);
        tipo.setNombre(nombre);
        return tipo;
    }

    private ZonaTuristica crearZona(Integer id, String nombre, String lat, String lon) {
        ZonaTuristica zona = new ZonaTuristica();
        zona.setId(id);
        zona.setNombre(nombre);
        zona.setLatitud(new BigDecimal(lat));
        zona.setLongitud(new BigDecimal(lon));
        zona.setEstacionCercana(origen);
        zona.setEstado("Activa");
        return zona;
    }

    private ZonaTipoTurismo relacion(ZonaTuristica zona, TipoTurismo tipo) {
        ZonaTipoTurismo rel = new ZonaTipoTurismo();
        rel.setZonaTuristica(zona);
        rel.setTipoTurismo(tipo);
        return rel;
    }

    private PreferenciaDTO preferencia(List<Integer> tipos, Integer minutos, String dificultad) {
        PreferenciaDTO dto = new PreferenciaDTO();
        dto.setIdEstacionOrigen(ID_ESTACION);
        dto.setIdsTipoTurismo(tipos);
        dto.setTiempoDisponibleMin(minutos);
        dto.setDificultad(dificultad);
        return dto;
    }

    @BeforeEach
    void prepararEscenario() {
        preferenciaService = new PreferenciaService(zonaTuristicaService, zonaTipoTurismoRepository,
                new RutaPeatonalService(rutaPeatonalRepository, dificultadRepository),
                estacionService, dificultadRepository);

        // RNF-06: los umbrales y el ritmo de caminata salen de la tabla.
        when(dificultadRepository.findAllByOrderByOrdenAsc()).thenReturn(NIVELES);
        NIVELES.forEach(nivel ->
                when(dificultadRepository.findByNombre(nivel.getNombre())).thenReturn(Optional.of(nivel)));

        origen = new Estacion();
        origen.setId(ID_ESTACION);
        origen.setNombre("Estacion Ollantaytambo");
        origen.setLatitud(new BigDecimal("-13.258600"));
        origen.setLongitud(new BigDecimal("-72.265000"));
        origen.setEstado("Activa");

        // 0.60 km de ida -> 1.20 km de circuito, 14 min, dificultad Baja.
        fortaleza = crearZona(6, "Conjunto Arqueologico de Ollantaytambo", "-13.254466", "-72.268563");
        // 5.00 km de ida -> 10.00 km de circuito, 250 min, dificultad Alta.
        llaqta = crearZona(8, "Llaqta de Machu Picchu", "-13.298600", "-72.288000");

        when(estacionService.buscarActivaPorId(ID_ESTACION)).thenReturn(origen);
        when(zonaTuristicaService.listarPorEstacion(ID_ESTACION)).thenReturn(List.of(fortaleza, llaqta));

        // CN-10: la fortaleza combina Historia/Cultura y Naturaleza a la vez.
        when(zonaTipoTurismoRepository.findByZonaTuristica_Id(6))
                .thenReturn(List.of(relacion(fortaleza, HISTORIA), relacion(fortaleza, NATURALEZA)));
        when(zonaTipoTurismoRepository.findByZonaTuristica_Id(8))
                .thenReturn(List.of(relacion(llaqta, AVENTURA)));
    }

    /** CN-10: una zona con dos categorias aparece buscando por cualquiera de ellas. */
    @Test
    void devuelveLaZonaTantoPorNaturalezaComoPorHistoriaCultura() {
        List<ZonaResultadoDTO> porNaturaleza = preferenciaService.buscarZonasRecomendadas(
                preferencia(List.of(NATURALEZA.getId()), 600, "Alta"));
        List<ZonaResultadoDTO> porHistoria = preferenciaService.buscarZonasRecomendadas(
                preferencia(List.of(HISTORIA.getId()), 600, "Alta"));

        assertThat(porNaturaleza).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
        assertThat(porHistoria).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
    }

    /** RF-03: el circuito debe caber en el tiempo declarado por el turista. */
    @Test
    void descartaLasZonasQueNoCabenEnElTiempoDisponible() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                preferencia(List.of(HISTORIA.getId(), AVENTURA.getId()), 30, "Alta"));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
    }

    /** La dificultad elegida es un techo: "Baja" excluye las rutas exigentes. */
    @Test
    void descartaLasZonasQueSuperanLaDificultadAceptada() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                preferencia(List.of(HISTORIA.getId(), AVENTURA.getId()), 600, "Baja"));

        assertThat(resultado).extracting(ZonaResultadoDTO::getNombre)
                .containsExactly("Conjunto Arqueologico de Ollantaytambo");
    }

    /** Cada resultado llega con su ruta ya calculada, para no recalcularla en la vista. */
    @Test
    void adjuntaLaRutaCalculadaACadaResultado() {
        List<ZonaResultadoDTO> resultado = preferenciaService.buscarZonasRecomendadas(
                preferencia(List.of(HISTORIA.getId(), AVENTURA.getId()), 600, "Alta"));

        assertThat(resultado).hasSize(2);
        assertThat(resultado.get(0).getRuta().getDistanciaKm()).isEqualByComparingTo("1.20");
        assertThat(resultado.get(0).getRuta().getEsIdaVuelta()).isTrue();
        // Ordenado por distancia: primero la ruta mas corta.
        assertThat(resultado.get(0).getRuta().getDistanciaKm())
                .isLessThan(resultado.get(1).getRuta().getDistanciaKm());
    }
}
