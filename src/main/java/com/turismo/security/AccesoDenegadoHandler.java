package com.turismo.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * HURF06: ante un rol no autorizado, redirige a la vista de acceso denegado
 * (auth/acceso-denegado.html).
 *
 * Tambien atiende el rechazo del token CSRF, que Spring Security senala con
 * una subclase de AccessDeniedException: ocurre cuando la sesion caduco con
 * un formulario abierto y el usuario lo envia despues. Se marca en la
 * peticion para que la vista explique que basta con recargar y reintentar,
 * en vez de hablarle de permisos que si tiene.
 */
@Component
public class AccesoDenegadoHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        request.setAttribute("sesionCaducada", accessDeniedException instanceof CsrfException);
        request.getRequestDispatcher("/acceso-denegado").forward(request, response);
    }
}
