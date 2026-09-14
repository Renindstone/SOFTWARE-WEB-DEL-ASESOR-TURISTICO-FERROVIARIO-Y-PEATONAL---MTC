package com.turismo.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone a todas las vistas la ruta de la peticion actual (sin el context
 * path) para que layout/nav.html marque el enlace activo de la barra
 * lateral. Thymeleaf 3.1 ya no permite leer #httpServletRequest desde la
 * plantilla, asi que el dato tiene que llegar por el modelo.
 */
@ControllerAdvice(basePackages = "com.turismo")
public class NavegacionAdvice {

    @ModelAttribute("rutaActual")
    public String rutaActual(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return "";
        }
        String contexto = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = request.getRequestURI();
        return uri.startsWith(contexto) ? uri.substring(contexto.length()) : uri;
    }
}
