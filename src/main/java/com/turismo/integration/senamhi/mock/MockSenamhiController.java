package com.turismo.integration.senamhi.mock;

import com.turismo.integration.senamhi.dto.PrevisionClimaSenamhiDTO;
import com.turismo.model.Estacion;
import com.turismo.repository.EstacionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulacion del servicio del SENAMHI (Sprint 0). Publica el mismo contrato
 * JSON que consumiria la API real, de modo que SenamhiClient hace una llamada
 * HTTP de verdad y la Capa de Integracion se ejercita completa: validacion del
 * feed (CB-03/CB-04), persistencia en PrevisionClima y registro en la
 * auditoria (RF-15/RF-16).
 *
 * Vive dentro de la aplicacion para que el sistema funcione con un solo
 * `docker compose up`, sin desplegar un segundo contenedor. La contrapartida
 * es que lee el catalogo de estaciones de la propia base de datos, cosa que
 * un SENAMHI real no haria: asi el mock cubre automaticamente cualquier
 * estacion que sincronice PeruRail, sin mantener aqui una lista paralela.
 *
 * Cuando el MTC consiga el acceso real basta con apagarlo
 * (integracion.senamhi.mock.habilitado=false) y apuntar
 * integracion.senamhi.base-url al servicio verdadero. Ni SenamhiClient ni el
 * job programado cambian.
 */
@RestController
@RequestMapping("/mock/senamhi")
@ConditionalOnProperty(name = "integracion.senamhi.mock.habilitado",
        havingValue = "true", matchIfMissing = true)
public class MockSenamhiController {

    /** Dias de pronostico que devuelve el feed si no se pide otra cosa. */
    private static final int DIAS_POR_DEFECTO = 7;

    /** Tope de dias, para que una peticion no pueda pedir un horizonte absurdo. */
    private static final int DIAS_MAXIMO = 30;

    private final EstacionRepository estacionRepository;
    private final GeneradorPronosticoSenamhi generador;

    public MockSenamhiController(EstacionRepository estacionRepository,
                                  GeneradorPronosticoSenamhi generador) {
        this.estacionRepository = estacionRepository;
        this.generador = generador;
    }

    /**
     * Pronostico de todas las estaciones desde hoy hacia adelante. Es el
     * endpoint que consume SincronizacionSenamhiJob todos los dias (RNF-03).
     *
     * Se incluyen tambien las estaciones inactivas: el clima de un lugar
     * existe aunque la estacion no admita pasajeros, y el filtrado por
     * EstEstado es una decision del MTC, no del servicio meteorologico.
     */
    @GetMapping("/previsiones")
    public List<PrevisionClimaSenamhiDTO> previsiones(
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) String desde) {

        int horizonte = acotarDias(dias);
        LocalDate inicio = parsearFecha(desde);

        List<PrevisionClimaSenamhiDTO> feed = new ArrayList<>();
        for (Estacion estacion : estacionRepository.findAll()) {
            for (int dia = 0; dia < horizonte; dia++) {
                feed.add(generador.generar(estacion, inicio.plusDays(dia)));
            }
        }
        return feed;
    }

    /** Pronostico de una sola estacion, util para revisar el mock a mano. */
    @GetMapping("/previsiones/{codigoEstacion}")
    public List<PrevisionClimaSenamhiDTO> previsionesDeEstacion(
            @PathVariable String codigoEstacion,
            @RequestParam(required = false) Integer dias,
            @RequestParam(required = false) String desde) {

        Estacion estacion = estacionRepository.findByCodigo(codigoEstacion)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "El SENAMHI no reporta la estación " + codigoEstacion));

        int horizonte = acotarDias(dias);
        LocalDate inicio = parsearFecha(desde);

        List<PrevisionClimaSenamhiDTO> feed = new ArrayList<>();
        for (int dia = 0; dia < horizonte; dia++) {
            feed.add(generador.generar(estacion, inicio.plusDays(dia)));
        }
        return feed;
    }

    private int acotarDias(Integer dias) {
        if (dias == null) {
            return DIAS_POR_DEFECTO;
        }
        return Math.max(1, Math.min(dias, DIAS_MAXIMO));
    }

    private LocalDate parsearFecha(String desde) {
        if (desde == null || desde.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(desde);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fecha inválida: " + desde + " (formato esperado aaaa-mm-dd)");
        }
    }
}
