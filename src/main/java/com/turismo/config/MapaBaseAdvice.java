package com.turismo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Pone a disposicion de todas las vistas la clave de CARTO con la que los
 * mapas (cliente/preferencias y cliente/ruta-detalle) piden las teselas
 * CartoDB Voyager.
 *
 * La clave es gratuita (5 millones de teselas al mes) pero obligatoria: sin
 * ella CARTO sirve las teselas estampadas con "API KEY REQUIRED". Llega por
 * la propiedad mapa.carto.api-key, que en application.properties se alimenta
 * de la variable de entorno CARTO_API_KEY para que no viva en el repositorio.
 * Cuando esta vacia, mapa-base.js recurre a las teselas estandar de
 * OpenStreetMap, de modo que el mapa nunca sale marcado ni vacio.
 *
 * Se limita a com.turismo.controller igual que GlobalExceptionHandler: el
 * BasicErrorController de Spring Boot no renderiza estas vistas.
 */
@ControllerAdvice(basePackages = "com.turismo.controller")
public class MapaBaseAdvice {

    private final String cartoApiKey;

    public MapaBaseAdvice(@Value("${mapa.carto.api-key:}") String cartoApiKey) {
        this.cartoApiKey = cartoApiKey == null ? "" : cartoApiKey.trim();
    }

    @ModelAttribute("cartoApiKey")
    public String cartoApiKey() {
        return cartoApiKey;
    }
}
