package com.turismo.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Manejador global de excepciones de negocio. Traduce cada excepcion del
 * dominio a la vista de error correspondiente, evitando que el usuario
 * final reciba un stacktrace crudo (RNF-02, usabilidad).
 *
 * CN-09 depende de este manejador: el mensaje de AforoCompletoException,
 * con la fecha alternativa sugerida, se muestra en la pantalla de error en
 * vez de propagarse como un HTTP 500.
 *
 * Se limita a com.turismo.controller a proposito: si abarcara todo el
 * contexto, tambien envolveria al BasicErrorController de Spring Boot, que
 * responde el cuerpo del error y no una vista.
 */
@ControllerAdvice(basePackages = "com.turismo.controller")
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** RF-16 (CN-09): aforo diario agotado para la fecha elegida. */
    @ExceptionHandler(AforoCompletoException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String manejarAforoCompleto(AforoCompletoException ex, Model model) {
        model.addAttribute("tituloError", "Aforo completo");
        model.addAttribute("mensajeError", ex.getMessage());
        return "error";
    }

    /** RF-02 (CN-02) y RNF-04 (CB-02): origen inactivo o circuito no caminable. */
    @ExceptionHandler({EstacionInactivaException.class, RutaInvalidaException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarRutaNoValida(RuntimeException ex, Model model) {
        model.addAttribute("tituloError", "No se pudo calcular la ruta");
        model.addAttribute("mensajeError", ex.getMessage());
        return "error";
    }

    /** RF-13/RF-14: datos rechazados de los feeds externos (CB-04, CB-06). */
    @ExceptionHandler({FeedInvalidoException.class, TarifaInvalidaException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarDatosExternosInvalidos(RuntimeException ex, Model model) {
        model.addAttribute("tituloError", "Datos externos inválidos");
        model.addAttribute("mensajeError", ex.getMessage());
        return "error";
    }

    /**
     * CN-05: validaciones del CRUD de zonas turisticas (preferencia, estacion)
     * y RF-12: reglas entre campos del servicio de tren.
     *
     * Es una red de seguridad: ServicioTrenController ya captura
     * ServicioTrenInvalidoException para devolver al formulario con lo que el
     * administrador habia tecleado, en vez de llevarlo a esta pantalla.
     */
    @ExceptionHandler({IllegalArgumentException.class, ServicioTrenInvalidoException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarValidacionDeNegocio(RuntimeException ex, Model model) {
        model.addAttribute("tituloError", "Datos no válidos");
        model.addAttribute("mensajeError", ex.getMessage());
        return "error";
    }

    /** Ultimo recurso: cualquier fallo no previsto tampoco debe llegar crudo al turista. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String manejarErrorInesperado(Exception ex, Model model) {
        LOG.error("Error no controlado en la aplicación", ex);
        model.addAttribute("tituloError", "No se pudo completar la operación");
        model.addAttribute("mensajeError",
                "Ocurrió un problema inesperado. Vuelve a intentarlo en unos minutos.");
        return "error";
    }
}
