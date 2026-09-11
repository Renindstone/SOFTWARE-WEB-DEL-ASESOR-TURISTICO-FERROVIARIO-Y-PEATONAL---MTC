package com.turismo.model;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La entidad Usuario repite la regla de correo del formulario de registro:
 * Hibernate la valida antes de insertar o actualizar, asi que un correo sin
 * dominio completo tampoco puede entrar por el alta administrativa.
 */
class UsuarioTest {

    private static ValidatorFactory factory;
    private static Validator validador;

    @BeforeAll
    static void abrirValidador() {
        factory = Validation.buildDefaultValidatorFactory();
        validador = factory.getValidator();
    }

    @AfterAll
    static void cerrarValidador() {
        if (factory != null) {
            factory.close();
        }
    }

    private Usuario conCorreo(String email) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario("ana_turista");
        usuario.setContrasenia("$2a$10$hashSimulado");
        usuario.setNombre("Ana Lucia");
        usuario.setApellidos("Perez Quispe");
        usuario.setEmail(email);
        return usuario;
    }

    private Set<String> camposConError(Usuario usuario) {
        return validador.validate(usuario).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "\"{0}\" es un correo valido")
    @ValueSource(strings = {"ana.perez@correo.pe", "carlos.torres@mtc.gob.pe", "usuario.123@sub.dominio.org"})
    @DisplayName("Correos con dominio completo: la entidad los acepta")
    void correosValidos(String email) {
        assertThat(camposConError(conCorreo(email))).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" es un correo invalido")
    @ValueSource(strings = {"usuario@gb", "admin@localhost", "sin_arroba.com", "usuario@dominio.c", ""})
    @DisplayName("Correos sin dominio completo, mal formados o vacíos: la entidad los rechaza")
    void correosInvalidos(String email) {
        assertThat(camposConError(conCorreo(email))).contains("email");
    }
}
