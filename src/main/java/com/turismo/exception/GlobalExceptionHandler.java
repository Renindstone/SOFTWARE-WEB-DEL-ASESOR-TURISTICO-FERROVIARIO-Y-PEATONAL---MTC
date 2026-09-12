package com.turismo.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Locale;
import java.util.stream.Collectors;

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

    /**
     * Direccion incompleta o con un valor que no tiene el tipo esperado (una
     * fecha "hoy", un identificador "abc", una edad "muchos"). Llega solo
     * escribiendo la URL a mano o desde un enlace roto: los formularios no
     * la producen, asi que basta con orientar al turista de vuelta al buscador.
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarPeticionMalformada(Exception ex, Model model) {
        LOG.debug("Petición malformada: {}", ex.getMessage());
        model.addAttribute("tituloError", "Datos no válidos");
        model.addAttribute("mensajeError",
                "A la dirección le falta algún dato o alguno tiene un formato que no se entiende. "
                        + "Vuelve al buscador y elige la ruta desde allí.");
        return "error";
    }

    /**
     * Restricciones que la validacion del formulario no cubrio y que la base
     * o Hibernate rechazaron al persistir (UNIQUE, CHECK, NOT NULL, longitud,
     * anotaciones de la entidad). Antes se mostraban como un error inesperado.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarIntegridad(DataIntegrityViolationException ex, Model model) {
        LOG.warn("Restricción de integridad rechazó la operación: {}", ex.getMostSpecificCause().getMessage());
        model.addAttribute("tituloError", "Datos no válidos");
        model.addAttribute("mensajeError", describirIntegridad(ex));
        return "error";
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String manejarViolacionDeEntidad(ConstraintViolationException ex, Model model) {
        model.addAttribute("tituloError", "Datos no válidos");
        model.addAttribute("mensajeError", ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining(". ")));
        return "error";
    }

    /** Traduce el nombre de la restriccion de PostgreSQL a la regla de negocio que la motiva. */
    private String describirIntegridad(DataIntegrityViolationException ex) {
        String causa = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (causa.contains("uq_zona_nombre")) {
            return "Ya existe una zona turística con ese nombre.";
        }
        if (causa.contains("uq_servicio_tramo_hora")) {
            return "Ya existe un servicio de tren con ese tramo y esa hora de salida.";
        }
        if (causa.contains("uq_usuario")) {
            return "El nombre de usuario o el correo electrónico ya están registrados.";
        }
        if (causa.contains("ck_zona_cupo")) {
            return "El cupo máximo diario debe ser mayor a cero.";
        }
        if (causa.contains("ck_zona_latitud") || causa.contains("ck_zona_longitud")) {
            return "Las coordenadas están fuera de rango (latitud entre -90 y 90, longitud entre -180 y 180).";
        }
        if (causa.contains("ck_servicio_tarifa") || causa.contains("ck_servicio_tiempo")) {
            return "La tarifa y el tiempo de tránsito del servicio deben ser mayores a cero.";
        }
        if (causa.contains("value too long") || causa.contains("numeric field overflow")) {
            return "Algún dato supera la longitud o el tamaño permitido.";
        }
        if (causa.contains("not-null") || causa.contains("null value")) {
            return "Falta un dato obligatorio.";
        }
        if (causa.contains("foreign key") || causa.contains("violates")) {
            return "Los datos hacen referencia a un registro que no existe o que sigue en uso.";
        }
        return "Los datos no cumplen las restricciones de la base de datos.";
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
