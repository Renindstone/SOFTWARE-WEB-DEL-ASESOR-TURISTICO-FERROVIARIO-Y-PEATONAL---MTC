package com.turismo.dto;

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
 * Restricciones del formulario de auto-registro.
 *
 * Los nombres y apellidos solo admiten caracteres propios de la identidad de
 * una persona, el nombre de usuario solo alfanumericos seguros, y el correo
 * debe llevar un dominio con extension (.com, .pe, .gob.pe...), de modo que
 * no puedan colarse cifras, simbolos ni direcciones a las que no se pueda
 * escribir.
 */
class RegistroUsuarioDTOTest {

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

    /** DTO por lo demas valido, para que solo falle el campo bajo prueba. */
    private RegistroUsuarioDTO valido() {
        RegistroUsuarioDTO dto = new RegistroUsuarioDTO();
        dto.setNombre("Ana Lucia");
        dto.setApellidos("Perez Quispe");
        dto.setEmail("ana.perez@correo.pe");
        dto.setNombreUsuario("ana_turista");
        dto.setContrasenia("Turista2026");
        dto.setConfirmarContrasenia("Turista2026");
        return dto;
    }

    private Set<String> camposConError(RegistroUsuarioDTO dto) {
        return validador.validate(dto).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    @ParameterizedTest(name = "\"{0}\" es un nombre valido")
    @ValueSource(strings = {
            "José María",     // tildes
            "Peña",           // ene
            "D'Onofrio",      // apostrofo
            "O'Higgins",
            "Jean-Pierre",    // guion
            "Güell",          // dieresis
            "Ana Lucia",
    })
    @DisplayName("Nombres con tildes, ñ, diéresis, guiones y apóstrofes: se aceptan")
    void nombresValidos(String nombre) {
        RegistroUsuarioDTO dto = valido();
        dto.setNombre(nombre);
        dto.setApellidos(nombre);
        assertThat(camposConError(dto)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" es un nombre invalido")
    @ValueSource(strings = {
            "Juan123",        // cifras
            "Carlos*$",       // simbolos
            "<script>",       // intento de inyeccion
            "Ana_Lucia",      // guion bajo: valido en usuario, no en un nombre
            "correo@ejemplo", // arroba
            "A",              // por debajo del minimo de dos caracteres
    })
    @DisplayName("Nombres con cifras o símbolos: se rechazan")
    void nombresInvalidos(String nombre) {
        RegistroUsuarioDTO dto = valido();
        dto.setNombre(nombre);
        assertThat(camposConError(dto)).contains("nombre");

        RegistroUsuarioDTO otro = valido();
        otro.setApellidos(nombre);
        assertThat(camposConError(otro)).contains("apellidos");
    }

    @ParameterizedTest(name = "\"{0}\" es un usuario valido")
    @ValueSource(strings = {"ana_turista", "turista.jose", "rail-luis", "abcd", "Usuario2026"})
    @DisplayName("Nombres de usuario alfanuméricos con punto, guion o guion bajo: se aceptan")
    void usuariosValidos(String usuario) {
        RegistroUsuarioDTO dto = valido();
        dto.setNombreUsuario(usuario);
        assertThat(camposConError(dto)).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\" es un usuario invalido")
    @ValueSource(strings = {
            "ana turista",    // espacio
            "ana$turista",    // simbolo
            "ana*",
            "ana@correo.pe",  // arroba
            "ana",            // por debajo del minimo de cuatro caracteres
    })
    @DisplayName("Nombres de usuario con espacios o símbolos: se rechazan")
    void usuariosInvalidos(String usuario) {
        RegistroUsuarioDTO dto = valido();
        dto.setNombreUsuario(usuario);
        assertThat(camposConError(dto)).contains("nombreUsuario");
    }

    @ParameterizedTest(name = "\"{0}\" es un correo valido")
    @ValueSource(strings = {
            "ana.perez@correo.pe",
            "turista@gmail.com",
            "carlos.torres@mtc.gob.pe",
            "usuario.123@sub.dominio.org"
    })
    @DisplayName("Correos con formato y dominio completo: se aceptan")
    void correosValidos(String email) {
        RegistroUsuarioDTO dto = valido();
        dto.setEmail(email);
        assertThat(camposConError(dto)).isEmpty();
    }

    // @Email por si solo acepta "usuario@gb" o "admin@localhost": es el
    // patron quien exige la extension del dominio.
    @ParameterizedTest(name = "\"{0}\" es un correo invalido")
    @ValueSource(strings = {
            "usuario@gb",          // sin TLD con punto
            "usuario@ytyt",
            "admin@localhost",
            "usuario@dominio",
            "sin_arroba.com",
            "@sinusuario.com",
            "usuario@dominio.c"    // extension de una sola letra
    })
    @DisplayName("Correos sin dominio completo o mal formados: se rechazan")
    void correosInvalidos(String email) {
        RegistroUsuarioDTO dto = valido();
        dto.setEmail(email);
        assertThat(camposConError(dto)).contains("email");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("Contraseña de más de 72 caracteres: se rechaza antes de llegar a BCrypt")
    void contraseniaDemasiadoLarga() {
        RegistroUsuarioDTO dto = valido();
        String larga = "p".repeat(73);
        dto.setContrasenia(larga);
        dto.setConfirmarContrasenia(larga);
        assertThat(camposConError(dto)).contains("contrasenia");
    }

    @org.junit.jupiter.api.Test
    @DisplayName("El DTO completo y correcto no produce ninguna violación")
    void dtoValido_sinViolaciones() {
        assertThat(camposConError(valido())).isEmpty();
    }
}
