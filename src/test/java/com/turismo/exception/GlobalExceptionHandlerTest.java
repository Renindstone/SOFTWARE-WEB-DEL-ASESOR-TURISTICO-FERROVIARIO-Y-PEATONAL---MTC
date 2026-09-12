package com.turismo.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lo que el usuario lee cuando algo llega mal formado o la base rechaza un
 * dato: siempre la vista de error con un mensaje en castellano que oriente,
 * nunca el texto crudo de Spring o de PostgreSQL (RNF-02).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Parámetro ausente en la URL: vista de error con orientación, no un 500")
    void parametroAusente() {
        Model model = new ConcurrentModel();

        String vista = handler.manejarPeticionMalformada(
                new MissingServletRequestParameterException("idEstacion", "Integer"), model);

        assertThat(vista).isEqualTo("error");
        assertThat(model.getAttribute("tituloError")).isEqualTo("Datos no válidos");
        assertThat((String) model.getAttribute("mensajeError")).contains("Vuelve al buscador");
    }

    @Test
    @DisplayName("Nombre de zona repetido (uq_zona_nombre): mensaje que nombra la regla")
    void zonaRepetida() {
        Model model = new ConcurrentModel();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_zona_nombre\""));

        handler.manejarIntegridad(ex, model);

        assertThat(model.getAttribute("mensajeError")).isEqualTo("Ya existe una zona turística con ese nombre.");
    }

    @Test
    @DisplayName("Tramo y hora de tren repetidos (uq_servicio_tramo_hora): mensaje propio")
    void servicioRepetido() {
        Model model = new ConcurrentModel();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert",
                new RuntimeException("duplicate key value violates unique constraint \"uq_servicio_tramo_hora\""));

        handler.manejarIntegridad(ex, model);

        assertThat((String) model.getAttribute("mensajeError")).contains("tramo y esa hora de salida");
    }

    @Test
    @DisplayName("Restricción no prevista: mensaje genérico, sin el texto de PostgreSQL")
    void restriccionDesconocida() {
        Model model = new ConcurrentModel();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("insert",
                new RuntimeException("ERROR: something exotic happened"));

        handler.manejarIntegridad(ex, model);

        assertThat((String) model.getAttribute("mensajeError"))
                .isEqualTo("Los datos no cumplen las restricciones de la base de datos.")
                .doesNotContain("exotic");
    }
}
