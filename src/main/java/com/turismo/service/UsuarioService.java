package com.turismo.service;

import com.turismo.dto.RegistroUsuarioDTO;
import com.turismo.model.Rol;
import com.turismo.model.Usuario;
import com.turismo.repository.RolRepository;
import com.turismo.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UsuarioService {

    /**
     * Unico rol que puede obtenerse por auto-registro. Los administrativos
     * (ADMIN_MTC, PERURAIL_ADMIN, TRAVEL_GROUP_USER) siguen creandose a mano,
     * porque dan acceso a los modulos de mantenimiento (RNF-05).
     */
    public static final String ROL_AUTO_REGISTRO = "TURISTA_PUBLICO";

    private static final String TABLA_AUDITADA = "usuario";

    /** Limite del algoritmo BCrypt (spring-security-crypto lo aplica al codificar). */
    private static final int LIMITE_BYTES_BCRYPT = 72;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          RolRepository rolRepository,
                          PasswordEncoder passwordEncoder,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> buscarPorId(Integer id) {
        return usuarioRepository.findById(id);
    }

    public Usuario registrar(Usuario usuario) {
        usuario.setContrasenia(passwordEncoder.encode(usuario.getContrasenia()));
        return usuarioRepository.save(usuario);
    }

    /**
     * Alta de un turista desde el formulario publico de registro.
     *
     * El rol se fija aqui a TURISTA_PUBLICO y no se toma de la peticion: si
     * el formulario decidiera el rol, cualquiera podria concederse ADMIN_MTC
     * enviando el campo a mano.
     *
     * Las comprobaciones de usuario y correo repetidos son por cortesia, para
     * dar un mensaje entendible; quien garantiza la unicidad son las
     * restricciones uq_usuario_nombre y uq_usuario_email de la base, que
     * siguen cubriendo el caso de dos altas simultaneas con el mismo dato.
     */
    @Transactional
    public Usuario registrarTurista(RegistroUsuarioDTO dto) {
        if (!dto.getContrasenia().equals(dto.getConfirmarContrasenia())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden");
        }
        // BCrypt cifra como maximo 72 bytes; con mas, el codificador lanza un
        // error en ingles que llegaba tal cual a la pantalla de registro.
        if (dto.getContrasenia().getBytes(StandardCharsets.UTF_8).length > LIMITE_BYTES_BCRYPT) {
            throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 caracteres)");
        }
        // El correo no distingue mayusculas: "Ana@Correo.pe" y "ana@correo.pe"
        // son la misma cuenta y no deben poder registrarse por separado.
        dto.setEmail(dto.getEmail().trim().toLowerCase(Locale.ROOT));

        if (usuarioRepository.findByNombreUsuario(dto.getNombreUsuario()).isPresent()) {
            throw new IllegalArgumentException("El nombre de usuario ya está en uso");
        }
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El correo electrónico ya está registrado");
        }

        Rol rolTurista = rolRepository.findByNombre(ROL_AUTO_REGISTRO)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el rol " + ROL_AUTO_REGISTRO + " en la base de datos"));

        Usuario usuario = new Usuario();
        usuario.setNombre(dto.getNombre());
        usuario.setApellidos(dto.getApellidos());
        usuario.setEmail(dto.getEmail());
        usuario.setNombreUsuario(dto.getNombreUsuario());
        usuario.setContrasenia(passwordEncoder.encode(dto.getContrasenia()));
        usuario.setRol(rolTurista);
        usuario.setEstado("Activo");

        Usuario guardado = usuarioRepository.save(usuario);

        // El autor es SISTEMA porque el alta ocurre sin sesion iniciada: no
        // hay todavia un usuario autenticado al que atribuir la operacion.
        auditoriaService.registrarAuditoria(AuditoriaService.USUARIO_SISTEMA, "INSERT", TABLA_AUDITADA,
                null, "Auto-registro turista: " + guardado.getNombreUsuario());

        return guardado;
    }
}
