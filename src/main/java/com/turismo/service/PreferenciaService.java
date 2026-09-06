package com.turismo.service;

import com.turismo.dto.PreferenciaDTO;
import com.turismo.dto.RutaCalculadaDTO;
import com.turismo.dto.ZonaResultadoDTO;
import com.turismo.exception.EstacionInactivaException;
import com.turismo.exception.RutaInvalidaException;
import com.turismo.model.Dificultad;
import com.turismo.model.Estacion;
import com.turismo.model.ZonaTipoTurismo;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.DificultadRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RF-01/RF-03: busca las zonas turisticas que encajan con lo que declara el
 * turista.
 *
 * Los cuatro criterios son filtros opcionales que se acumulan: una consulta
 * sin ninguno devuelve el catalogo completo de zonas activas, y cada filtro
 * que el turista anade lo recorta. Asi puede explorar primero y precisar
 * despues, en vez de tener que acertar las cuatro respuestas antes de ver
 * nada.
 */
@Service
public class PreferenciaService {

    private final ZonaTuristicaService zonaTuristicaService;
    private final RutaPeatonalService rutaPeatonalService;
    private final EstacionService estacionService;
    private final DificultadRepository dificultadRepository;

    public PreferenciaService(ZonaTuristicaService zonaTuristicaService,
                               RutaPeatonalService rutaPeatonalService,
                               EstacionService estacionService,
                               DificultadRepository dificultadRepository) {
        this.zonaTuristicaService = zonaTuristicaService;
        this.rutaPeatonalService = rutaPeatonalService;
        this.estacionService = estacionService;
        this.dificultadRepository = dificultadRepository;
    }

    /**
     * CU-02: zonas que superan todos los filtros declarados, cada una con su
     * ruta de ida y vuelta ya calculada para que la vista no tenga que
     * recalcularla zona por zona (RNF-01).
     *
     * El circuito se mide desde la estacion que eligio el turista. Si no
     * eligio ninguna, cada zona se mide desde su propia estacion cercana
     * (ZonIdEstacionCercana), que es el acceso natural en tren: de ese modo
     * el tiempo y la dificultad siguen siendo datos reales y los otros dos
     * filtros conservan sentido sin haber declarado estacion.
     */
    @Transactional(readOnly = true)
    public List<ZonaResultadoDTO> buscarZonasRecomendadas(PreferenciaDTO preferencia) {
        Estacion estacionElegida = preferencia.getIdEstacionOrigen() == null
                ? null
                : estacionService.buscarActivaPorId(preferencia.getIdEstacionOrigen());

        // Una sola consulta con estacion y tipos ya resueltos: el listado sin
        // filtros pinta el catalogo entero y no puede permitirse un N+1.
        List<ZonaTuristica> zonas = zonaTuristicaService.listarActivasConEstacionYTipos();

        // Los niveles y el techo de dificultad se leen una vez para toda la
        // busqueda, no una vez por zona.
        List<Dificultad> niveles = dificultadRepository.findAllByOrderByOrdenAsc();
        Short techoDificultad = resolverTechoDificultad(preferencia.getDificultad());

        List<ZonaResultadoDTO> resultados = new ArrayList<>();
        for (ZonaTuristica zona : zonas) {
            Estacion origen = estacionElegida != null ? estacionElegida : zona.getEstacionCercana();
            if (!cumpleEstacion(zona, estacionElegida)
                    || !esAlcanzable(origen)
                    || !cumpleTipoTurismo(zona, preferencia.getIdsTipoTurismo())) {
                continue;
            }
            RutaCalculadaDTO ruta;
            try {
                ruta = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(origen, zona, niveles);
            } catch (RutaInvalidaException | EstacionInactivaException ex) {
                // Sin circuito caminable (CB-02): la zona coincide con la
                // estacion y no hay nada que caminar.
                continue;
            }
            if (!cumpleTiempoDisponible(ruta, preferencia.getTiempoDisponibleMin())
                    || !cumpleDificultad(ruta, techoDificultad)) {
                continue;
            }
            resultados.add(mapearADto(zona, origen, ruta));
        }
        resultados.sort((a, b) -> a.getRuta().getDistanciaKm().compareTo(b.getRuta().getDistanciaKm()));
        return resultados;
    }

    /**
     * RNF-06: los niveles que ofrece el selector salen de la tabla
     * dificultad, no de una lista fija en la vista, para que anadir o
     * renombrar un nivel no obligue a tocar la plantilla.
     */
    @Transactional(readOnly = true)
    public List<Dificultad> listarDificultades() {
        return dificultadRepository.findAllByOrderByOrdenAsc();
    }

    /** RF-02: sin estacion declarada no se descarta ninguna zona. */
    private boolean cumpleEstacion(ZonaTuristica zona, Estacion estacionElegida) {
        return estacionElegida == null
                || estacionElegida.getId().equals(zona.getEstacionCercana().getId());
    }

    /** CN-10: basta con que la zona tenga una de las categorias marcadas. */
    private boolean cumpleTipoTurismo(ZonaTuristica zona, List<Integer> idsTipoTurismo) {
        if (idsTipoTurismo == null || idsTipoTurismo.isEmpty()) {
            return true;
        }
        return zona.getTiposTurismo().stream()
                .anyMatch(rel -> idsTipoTurismo.contains(rel.getTipoTurismo().getId()));
    }

    /** El circuito completo debe caber en el tiempo que declaro el turista. */
    private boolean cumpleTiempoDisponible(RutaCalculadaDTO ruta, Integer tiempoDisponibleMin) {
        return tiempoDisponibleMin == null || ruta.getTiempoEstimadoMin() <= tiempoDisponibleMin;
    }

    /**
     * CN-02: una zona a la que solo se llega por una estacion dada de baja no
     * se ofrece. Se comprueba antes de calcular para no apoyarse en la
     * excepcion del motor de rutas como si fuera flujo normal.
     */
    private boolean esAlcanzable(Estacion origen) {
        return origen != null && "Activa".equalsIgnoreCase(origen.getEstado());
    }

    /**
     * La dificultad elegida es un techo: quien acepta "Alta" tambien ve las
     * mas suaves. La comparacion usa DifOrden de la tabla parametrica, de modo
     * que anadir un nivel nuevo no obliga a tocar este codigo (RNF-06).
     */
    private Short resolverTechoDificultad(String dificultadMaxima) {
        if (dificultadMaxima == null || dificultadMaxima.isBlank()) {
            return null;
        }
        return dificultadRepository.findByNombre(dificultadMaxima)
                .map(Dificultad::getOrden)
                .orElse(null);
    }

    private boolean cumpleDificultad(RutaCalculadaDTO ruta, Short techo) {
        Short nivelRuta = ruta.getOrdenDificultad();
        return techo == null || nivelRuta == null || nivelRuta <= techo;
    }

    private ZonaResultadoDTO mapearADto(ZonaTuristica zona, Estacion origen, RutaCalculadaDTO ruta) {
        ZonaResultadoDTO dto = new ZonaResultadoDTO();
        dto.setIdZona(zona.getId());
        dto.setNombre(zona.getNombre());
        dto.setDescripcion(zona.getDescripcion());
        dto.setCostoAproximado(zona.getCostoAprox());
        dto.setCupoMaximoDiario(zona.getCupoMaximoDiario());
        dto.setIdEstacionCercana(origen.getId());
        dto.setNombreEstacionCercana(origen.getNombre());
        dto.setLatitud(zona.getLatitud());
        dto.setLongitud(zona.getLongitud());
        dto.setRuta(ruta);
        dto.setTiposTurismo(zona.getTiposTurismo().stream()
                .map(ZonaTipoTurismo::getTipoTurismo)
                .map(tipo -> tipo.getNombre())
                .collect(Collectors.toList()));
        return dto;
    }
}
