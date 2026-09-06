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
import java.util.List;

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
     * ambos puntos (seccion 5.1). Si la distancia de ida es cero, lanza
     * RutaInvalidaException (no existe circuito caminable).
     */
    @Transactional(readOnly = true)
    public RutaCalculadaDTO calcularRutaPeatonalIdaVuelta(Estacion origen, ZonaTuristica destino) {
        validarOrigenActivo(origen);

        BigDecimal distanciaIda = HaversineCalculator.calcularDistanciaKm(
                origen.getLatitud(), origen.getLongitud(),
                destino.getLatitud(), destino.getLongitud());

        if (distanciaIda.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RutaInvalidaException(
                    "La zona turística debe encontrarse a una distancia caminable mayor a cero");
        }

        BigDecimal distanciaIdaVuelta = distanciaIda.multiply(BigDecimal.valueOf(2));
        Dificultad dificultad = clasificarDificultad(distanciaIdaVuelta);

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
     * Primer nivel cuyo tope de distancia cubre el circuito. El nivel mas
     * exigente no tiene tope (DifDistanciaMaximaKm nulo) y actua de cajon de
     * sastre para los recorridos largos.
     */
    private Dificultad clasificarDificultad(BigDecimal distanciaIdaVuelta) {
        List<Dificultad> niveles = dificultadRepository.findAllByOrderByOrdenAsc();
        if (niveles.isEmpty()) {
            throw new IllegalStateException(
                    "No hay niveles de dificultad configurados en la tabla dificultad");
        }
        return niveles.stream()
                .filter(nivel -> nivel.getDistanciaMaximaKm() == null
                        || distanciaIdaVuelta.compareTo(nivel.getDistanciaMaximaKm()) <= 0)
                .findFirst()
                .orElse(niveles.get(niveles.size() - 1));
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
