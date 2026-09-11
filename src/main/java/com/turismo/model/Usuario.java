package com.turismo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuario")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "UsuIdUsuario")
    private Integer id;

    @Column(name = "UsuNombreUsuario", length = 50, nullable = false, unique = true)
    private String nombreUsuario;

    @Column(name = "UsuContrasenia", length = 100, nullable = false)
    private String contrasenia;

    @Column(name = "UsuNombre", length = 50, nullable = false)
    private String nombre;

    @Column(name = "UsuApellidos", length = 50, nullable = false)
    private String apellidos;

    /**
     * Mismo patron que RegistroUsuarioDTO: un correo sin extension de dominio
     * no puede llegar a la tabla por ningun camino, tampoco por el alta
     * administrativa. Hibernate valida la entidad antes de insertar o
     * actualizar (jakarta.persistence.validation.mode=auto).
     *
     * El nombre de columna va sin comillas escapadas porque
     * hibernate.globally_quoted_identifiers=true ya las anade a todos los
     * identificadores; escribirlas aqui las duplicaria y ddl-auto=validate
     * no encontraria la columna.
     */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email no es válido")
    @Pattern(
        regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
        message = "El email debe incluir un dominio válido (ejemplo: usuario@correo.com o usuario@correo.pe)"
    )
    @Column(name = "UsuEmail", nullable = false, length = 50, unique = true)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UsuIdRol", nullable = false)
    private Rol rol;

    @Column(name = "UsuEstado", length = 10, nullable = false)
    private String estado = "Activo";

}
