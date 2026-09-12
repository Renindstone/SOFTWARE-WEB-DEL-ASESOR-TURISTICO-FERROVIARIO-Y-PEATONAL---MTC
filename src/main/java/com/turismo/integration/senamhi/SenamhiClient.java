package com.turismo.integration.senamhi;

import com.turismo.exception.FeedInvalidoException;
import com.turismo.integration.senamhi.dto.PrevisionClimaSenamhiDTO;
import com.turismo.model.Estacion;
import com.turismo.model.PrevisionClima;
import com.turismo.repository.EstacionRepository;
import com.turismo.service.ClimaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Adaptador hacia el servicio/API del SENAMHI: previsiones climaticas por
 * estacion/coordenadas (RF-15). Consumido por SincronizacionSenamhiJob
 * (RNF-03).
 */
@Component
public class SenamhiClient {

    private static final Logger LOG = LoggerFactory.getLogger(SenamhiClient.class);
    private static final BigDecimal LLUVIA_MINIMA = BigDecimal.ZERO;
    private static final BigDecimal LLUVIA_MAXIMA = BigDecimal.valueOf(100);

    private final RestClient senamhiRestClient;
    private final ClimaService climaService;
    private final EstacionRepository estacionRepository;

    /** Dias de pronostico que se piden al servicio en cada sincronizacion. */
    @Value("${integracion.senamhi.dias-pronostico:7}")
    private int diasPronostico;

    public SenamhiClient(RestClient senamhiRestClient,
                          ClimaService climaService,
                          EstacionRepository estacionRepository) {
        this.senamhiRestClient = senamhiRestClient;
        this.climaService = climaService;
        this.estacionRepository = estacionRepository;
    }

    /**
     * Obtiene el pronostico publicado por SENAMHI para todas las estaciones,
     * desde hoy y por los proximos dias-pronostico dias. Traer varios dias de
     * una vez permite que el turista consulte una fecha de visita futura
     * (CU-03) sin depender de que la tarea programada corra ese mismo dia.
     */
    public List<PrevisionClimaSenamhiDTO> obtenerPrevisiones() {
        try {
            List<PrevisionClimaSenamhiDTO> previsiones = senamhiRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/previsiones")
                            .queryParam("dias", diasPronostico)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PrevisionClimaSenamhiDTO>>() {
                    });
            return previsiones == null ? List.of() : previsiones;
        } catch (RestClientException ex) {
            LOG.warn("No se pudo consultar el pronóstico del SENAMHI: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Caso de prueba CB-03/CB-04: valida el feed recibido de SENAMHI y lo
     * descarta si la probabilidad de lluvia esta fuera del rango 0-100.
     * Cuando es valido, delega en ClimaService el insert/update de
     * PrevisionClima (RF-15).
     */
    public void procesarFeedSenamhi(PrevisionClimaSenamhiDTO feed) {
        BigDecimal probabilidad = feed.getProbabilidadLluvia();
        if (probabilidad == null
                || probabilidad.compareTo(LLUVIA_MINIMA) < 0
                || probabilidad.compareTo(LLUVIA_MAXIMA) > 0) {
            throw new FeedInvalidoException("Probabilidad de lluvia fuera de rango (0-100)");
        }

        Estacion estacion = resolverEstacion(feed);
        if (estacion == null) {
            return;
        }

        PrevisionClima datos = new PrevisionClima();
        datos.setTemperaturaMinC(feed.getTemperaturaMinimaC());
        datos.setTemperaturaMaxC(feed.getTemperaturaMaximaC());
        datos.setProbabilidadLluvia(probabilidad);
        datos.setEstadoClima(feed.getEstadoClima());

        climaService.guardarOActualizar(estacion, parsearFecha(feed.getFecha()), datos);
    }

    /**
     * El feed identifica la estacion por su EstCodigo. Un codigo desconocido
     * no invalida el lote completo: se registra y se omite ese renglon, a la
     * espera de que la sincronizacion de PeruRail lo incorpore.
     */
    private Estacion resolverEstacion(PrevisionClimaSenamhiDTO feed) {
        if (feed.getCodigoEstacion() == null) {
            LOG.warn("Registro del SENAMHI sin código de estación; se omite");
            return null;
        }
        return estacionRepository.findByCodigo(feed.getCodigoEstacion())
                .orElseGet(() -> {
                    LOG.warn("El SENAMHI reportó la estación desconocida '{}'; se omite",
                            feed.getCodigoEstacion());
                    return null;
                });
    }

    private LocalDate parsearFecha(String fecha) {
        if (fecha == null || fecha.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(fecha);
        } catch (DateTimeParseException ex) {
            throw new FeedInvalidoException("Fecha de previsión con formato inválido: " + fecha);
        }
    }
}
