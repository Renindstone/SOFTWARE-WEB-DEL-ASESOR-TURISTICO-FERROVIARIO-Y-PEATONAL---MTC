package com.turismo.integration.perurail;

import com.turismo.integration.perurail.dto.EstacionPeruRailDTO;
import com.turismo.integration.perurail.dto.ServicioTrenPeruRailDTO;
import com.turismo.exception.TarifaInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adaptador hacia el servicio/API de PeruRail: catalogo de estaciones,
 * horarios, tiempos de transito y tarifas (RF-14). Consumido por
 * SincronizacionPeruRailJob (RNF-03).
 *
 * Mientras el feed real no este disponible, la URL base apunta al mock
 * definido en el Sprint 0 (integracion.perurail.base-url). Si el servicio no
 * responde, se devuelve una lista vacia y la sincronizacion queda sin
 * cambios, en vez de tumbar la tarea programada.
 */
@Component
public class PeruRailClient {

    private static final Logger LOG = LoggerFactory.getLogger(PeruRailClient.class);

    private final RestClient peruRailRestClient;

    public PeruRailClient(RestClient peruRailRestClient) {
        this.peruRailRestClient = peruRailRestClient;
    }

    /** Obtiene el catalogo de estaciones activas publicado por PeruRail. */
    public List<EstacionPeruRailDTO> obtenerEstaciones() {
        try {
            List<EstacionPeruRailDTO> estaciones = peruRailRestClient.get()
                    .uri("/estaciones")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<EstacionPeruRailDTO>>() {
                    });
            return estaciones == null ? List.of() : estaciones;
        } catch (RestClientException ex) {
            LOG.warn("No se pudo consultar el catálogo de estaciones de PeruRail: {}", ex.getMessage());
            return List.of();
        }
    }

    /** Obtiene horarios y tarifas de los servicios de tren disponibles. */
    public List<ServicioTrenPeruRailDTO> obtenerServicios() {
        try {
            List<ServicioTrenPeruRailDTO> servicios = peruRailRestClient.get()
                    .uri("/servicios")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ServicioTrenPeruRailDTO>>() {
                    });
            return servicios == null ? List.of() : servicios;
        } catch (RestClientException ex) {
            LOG.warn("No se pudo consultar los servicios de PeruRail: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Caso de prueba CB-05/CB-06: valida que la tarifa recibida sea un
     * valor numerico positivo antes de habilitar su persistencia.
     */
    public boolean validarTarifaPeruRail(BigDecimal tarifa) {
        if (tarifa == null || tarifa.compareTo(BigDecimal.ZERO) <= 0) {
            throw new TarifaInvalidaException("La tarifa debe ser un valor mayor a cero");
        }
        return true;
    }
}
