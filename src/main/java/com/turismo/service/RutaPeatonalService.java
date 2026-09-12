package com.turismo.service;

import com.turismo.dto.RutaCalculadaDTO;
import com.turismo.exception.EstacionInactivaException;
import com.turismo.exception.RutaInvalidaException;
import com.turismo.model.Dificultad;
import com.turismo.model.Estacion;
import com.turismo.model.RutaPeatonal;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.DificultadRepository;
import com.turismo.repository.RutaPeatonalRepository;
import com.turismo.util.HaversineCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * RF-04/RF-05/RNF-04: motor de calculo de la ruta peatonal de ida y
 * vuelta, usando la formula de Haversine (ver 5.1 del documento).
 * Caja Blanca: CB-01 (ruta valida), CB-02 (distancia cero -> excepcion).
 *
 * Los umbrales de distancia y el ritmo de caminata salen de la tabla
 * parametrica dificultad, no de constantes de esta clase: el RNF-06 pide
 * poder ajustarlos sin tocar el codigo fuente.
 */
@Service
public class RutaPeatonalService {

    private final RutaPeatonalRepository rutaPeatonalRepository;
    private final DificultadRepository dificultadRepository;

    public RutaPeatonalService(RutaPeatonalRepository rutaPeatonalRepository,
                                DificultadRepository dificultadRepository) {
        this.rutaPeatonalRepository = rutaPeatonalRepository;
        this.dificultadRepository = dificultadRepository;
    }

    /**
     * CB-01/CB-02: calcula el circuito de ida y vuelta entre la estacion de
     * origen y la zona turistica de destino, a partir de las coordenadas de
     * ambos puntos (seccion 5.1). Si la distancia de ida es cero, o el
     * circuito supera el tope del nivel de dificultad mas exigente, lanza
     * RutaInvalidaException (no existe circuito caminable).
     */
    public RutaCalculadaDTO calcularRutaPeatonalIdaVuelta(Estacion origen, ZonaTuristica destino) {
        return calcularRutaPeatonalIdaVuelta(origen, destino,
                dificultadRepository.findAllByOrderByOrdenAsc());
    }

    /**
     * Variante para quien calcula muchas rutas seguidas (el buscador de
     * zonas): recibe los niveles ya leidos y no vuelve a consultarlos una vez
     * por zona (RNF-01).
     *
     * Deliberadamente sin @Transactional: esto es un calculo, no una unidad
     * de trabajo. Con una transaccion propia, la excepcion de una zona
     * descartable marcaba como rollback-only la transaccion del llamador y
     * tumbaba la busqueda entera aunque el llamador capturase la excepcion.
     */
    public RutaCalculadaDTO calcularRutaPeatonalIdaVuelta(Estacion origen, ZonaTuristica destino,
                                                           List<Dificultad> niveles) {
        validarOrigenActivo(origen);

        BigDecimal distanciaIda = HaversineCalculator.calcularDistanciaKm(
                origen.getLatitud(), origen.getLongitud(),
                destino.getLatitud(), destino.getLongitud());

        if (distanciaIda.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RutaInvalidaException(
                    "La zona turística debe encontrarse a una distancia caminable mayor a cero");
        }

        BigDecimal distanciaIdaVuelta = distanciaIda.multiply(BigDecimal.valueOf(2));
        Dificultad dificultad = clasificarDificultad(distanciaIdaVuelta, niveles)
                .orElseThrow(() -> new RutaInvalidaException(
                        "Desde " + origen.getNombre() + ", la zona turística " + destino.getNombre()
                                + " queda a " + distanciaIda + " km: el circuito de ida y vuelta ("
                                + distanciaIdaVuelta + " km) supera los "
                                + topeCaminable(niveles).stripTrailingZeros().toPlainString()
                                + " km que admite un recorrido a pie. Elige una zona cercana a esa estación "
                                + "o la estación desde la que se accede a la zona."));

        RutaCalculadaDTO dto = new RutaCalculadaDTO();
        dto.setNombre("Circuito " + origen.getNombre() + " - " + destino.getNombre());
        dto.setDistanciaKm(distanciaIdaVuelta);
        dto.setTiempoEstimadoMin(calcularTiempoEstimadoMin(distanciaIdaVuelta, dificultad));
        dto.setDificultad(dificultad.getNombre());
        dto.setOrdenDificultad(dificultad.getOrden());
        dto.setEsIdaVuelta(Boolean.TRUE);
        return dto;
    }

    /**
     * RF-02: una estacion marcada como Inactiva no puede usarse como punto de
     * partida, aunque se alcance el calculo por URL directa saltando el
     * selector del formulario.
     */
    private void validarOrigenActivo(Estacion origen) {
        if (!"Activa".equalsIgnoreCase(origen.getEstado())) {
            throw new EstacionInactivaException(
                    "La estación " + origen.getNombre() + " está inactiva y no puede usarse como punto de partida");
        }
    }

    /**
     * Primer nivel cuyo tope de distancia cubre el circuito. Si ningun nivel
     * lo cubre, el recorrido no es caminable y no hay ruta: el tope del nivel
     * mas exigente (40 km de ida y vuelta en la carga inicial) es el limite
     * de lo que el sistema acepta a pie, y vive en la tabla dificultad para
     * poder ajustarlo sin tocar el codigo (RNF-06). Un nivel con
     * DifDistanciaMaximaKm nulo sigue actuando de cajon de sastre sin tope,
     * por si una instalacion prefiere no limitar.
     *
     * Sin este corte, por URL se podia pedir el "circuito" Puno - Machu
     * Picchu, unos 800 km, y el sistema lo clasificaba como Alta.
     */
    private Optional<Dificultad> clasificarDificultad(BigDecimal distanciaIdaVuelta, List<Dificultad> niveles) {
        if (niveles == null || niveles.isEmpty()) {
            throw new IllegalStateException(
                    "No hay niveles de dificultad configurados en la tabla dificultad");
        }
        return niveles.stream()
                .filter(nivel -> nivel.getDistanciaMaximaKm() == null
                        || distanciaIdaVuelta.compareTo(nivel.getDistanciaMaximaKm()) <= 0)
                .findFirst();
    }

    /** Mayor tope configurado: el circuito mas largo que todavia se considera caminable. */
    private BigDecimal topeCaminable(List<Dificultad> niveles) {
        return niveles.stream()
                .map(nivel -> nivel.getDistanciaMaximaKm())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    /** Tiempo minimo de 1 minuto: RutTiempoEstimadoMin tiene CHECK > 0. */
    private int calcularTiempoEstimadoMin(BigDecimal distanciaIdaVuelta, Dificultad dificultad) {
        int minutos = distanciaIdaVuelta
                .multiply(BigDecimal.valueOf(dificultad.getVelocidadMinPorKm()))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();
        return Math.max(minutos, 1);
    }

    /**
     * RNF-04: persiste el circuito calculado como RutaPeatonal, reutilizando
     * la ruta ya registrada para el mismo par estacion-zona en vez de
     * duplicarla en cada consulta (uq_ruta_origen_zona). La entidad exige
     * RutEsIdaVuelta = TRUE, regla de negocio del caso.
     */
    @Transactional
    public RutaPeatonal obtenerOCrearRuta(Estacion origen, ZonaTuristica destino, RutaCalculadaDTO calculo) {
        RutaPeatonal ruta = rutaPeatonalRepository
                .findFirstByEstacionOrigen_IdAndZonaDestino_Id(origen.getId(), destino.getId())
                .orElseGet(RutaPeatonal::new);

        ruta.setNombre(recortar(calculo.getNombre(), 100));
        if (ruta.getDescripcion() == null) {
            ruta.setDescripcion(recortar("Circuito peatonal de ida y vuelta entre "
                    + origen.getNombre() + " y " + destino.getNombre() + ".", 500));
        }
        ruta.setDistanciaKm(calculo.getDistanciaKm());
        ruta.setTiempoEstimadoMin(calculo.getTiempoEstimadoMin());
        ruta.setDificultad(dificultadRepository.findByNombre(calculo.getDificultad())
                .orElseThrow(() -> new IllegalStateException(
                        "Nivel de dificultad no registrado: " + calculo.getDificultad())));
        ruta.setEstacionOrigen(origen);
        ruta.setZonaDestino(destino);
        ruta.setEsIdaVuelta(Boolean.TRUE);

        return rutaPeatonalRepository.save(ruta);
    }

    private String recortar(String texto, int maximo) {
        return texto != null && texto.length() > maximo ? texto.substring(0, maximo) : texto;
    }

    public RutaPeatonal guardar(RutaPeatonal ruta) {
        return rutaPeatonalRepository.save(ruta);
    }
}
