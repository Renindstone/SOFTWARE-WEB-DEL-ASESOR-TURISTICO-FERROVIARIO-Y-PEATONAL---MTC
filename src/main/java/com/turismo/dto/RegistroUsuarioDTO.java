package com.turismo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 50, message = "El nombre no puede superar los 50 caracteres")
    private String nombre;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 50, message = "Los apellidos no pueden superar los 50 caracteres")
    private String apellidos;

    @NotBlank(message = "El correo electrónico es obligatorio")
    @Email(message = "El correo electrónico no tiene un formato válido")
    @Size(max = 50, message = "El correo electrónico no puede superar los 50 caracteres")
    private String email;

    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 4, max = 50, message = "El nombre de usuario debe tener entre 4 y 50 caracteres")
    private String nombreUsuario;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    private String contrasenia;

    @NotBlank(message = "Debe repetir la contraseña")
    private String confirmarContrasenia;
}
