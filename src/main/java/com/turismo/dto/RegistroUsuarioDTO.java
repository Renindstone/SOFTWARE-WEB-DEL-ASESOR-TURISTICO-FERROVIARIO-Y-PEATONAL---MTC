package com.turismo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Datos del formulario de auto-registro del turista (vista auth/registro).
 *
 * No es una entidad: recoge la contrasena en claro y repetida, que nunca
 * deben llegar a la tabla usuario. UsuarioService.registrarTurista es quien
 * comprueba que ambas coincidan, cifra la buena y descarta el resto.
 *
 * Las longitudes replican las del diccionario de datos (seccion 6.4) para
 * que el formulario rechace lo que la base rechazaria despues: UsuNombre y
 * UsuApellidos con 50, UsuEmail con 50 y UsuNombreUsuario con 50.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistroUsuarioDTO {

    /**
     * El guion va al final de la clase de caracteres para que se lea como un
     * guion literal y no como un rango. Se admiten el guion y el apostrofo
     * porque forman parte de apellidos reales (D'Onofrio, O'Higgins).
     */
    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    @Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]+$",
        message = "El nombre solo puede contener letras, espacios, guiones y apóstrofes (sin números ni símbolos)"
    )
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(min = 2, max = 50, message = "Los apellidos deben tener entre 2 y 50 caracteres")
    @Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]+$",
        message = "Los apellidos solo pueden contener letras, espacios, guiones y apóstrofes (sin números ni símbolos)"
    )
    private String apellidos;

    /**
     * @Email admite dominios sin extension ("usuario@gb", "admin@localhost"),
     * validos en una intranet pero inutiles para escribir a un turista. El
     * patron exige ademas un dominio con extension de al menos dos letras
     * (.com, .pe, .gob.pe, .org). El mismo patron vive en la entidad Usuario
     * y en el atributo pattern del formulario.
     */
    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
        message = "El correo electrónico debe incluir un dominio válido (ejemplo: usuario@correo.com o usuario@correo.pe)"
    )
    @Size(max = 50, message = "El correo electrónico no puede superar los 50 caracteres")
    private String email;

    /** Sin espacios ni simbolos: es la credencial con la que se inicia sesion. */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 50, message = "El nombre de usuario debe tener entre 4 y 50 caracteres")
    @Pattern(
        regexp = "^[a-zA-Z0-9_.-]+$",
        message = "El nombre de usuario solo puede contener letras, números, puntos, guiones y guiones bajos"
    )
    private String nombreUsuario;

    /** BCrypt solo cifra los primeros 72 bytes; por encima, el codificador falla. */
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, max = 72, message = "La contraseña debe tener entre 6 y 72 caracteres")
    private String contrasenia;

    @NotBlank(message = "Debe repetir la contraseña")
    private String confirmarContrasenia;
}
