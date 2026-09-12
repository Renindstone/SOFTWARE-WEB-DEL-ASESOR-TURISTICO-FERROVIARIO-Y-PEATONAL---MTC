package com.turismo.service;

import com.turismo.dto.RegistroUsuarioDTO;
import com.turismo.model.Rol;
import com.turismo.model.Usuario;
import com.turismo.repository.RolRepository;
import com.turismo.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Auto-registro del turista publico (formulario auth/registro).
 *
 * Interesa sobre todo que el rol no dependa de lo que llegue en la
 * peticion: es lo que impide que alguien se conceda un rol
 * administrativo desde el formulario publico (RNF-05).
 */
@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private RolRepository rolRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private UsuarioService usuarioService;

    private RegistroUsuarioDTO dto;
    private Rol rolTurista;

    @BeforeEach
    void setUp() {
        dto = new RegistroUsuarioDTO();
        dto.setNombre("Ana Lucia");
        dto.setApellidos("Perez Quispe");
        dto.setEmail("ana.perez@correo.pe");
        dto.setNombreUsuario("ana_turista");
        dto.setContrasenia("Turista2026");
        dto.setConfirmarContrasenia("Turista2026");

        rolTurista = new Rol();
        rolTurista.setId(4);
        rolTurista.setNombre("TURISTA_PUBLICO");
    }

    @Test
    @DisplayName("Alta correcta: cifra la contrasena, asigna TURISTA_PUBLICO y deja traza")
    void registrarTurista_datosValidos_persisteYAudita() {
        when(usuarioRepository.findByNombreUsuario("ana_turista")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana.perez@correo.pe")).thenReturn(Optional.empty());
        when(rolRepository.findByNombre("TURISTA_PUBLICO")).thenReturn(Optional.of(rolTurista));
        when(passwordEncoder.encode("Turista2026")).thenReturn("$2a$10$hashSimulado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario resultado = usuarioService.registrarTurista(dto);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();

        assertThat(guardado.getNombreUsuario()).isEqualTo("ana_turista");
        assertThat(guardado.getNombre()).isEqualTo("Ana Lucia");
        assertThat(guardado.getApellidos()).isEqualTo("Perez Quispe");
        assertThat(guardado.getEmail()).isEqualTo("ana.perez@correo.pe");
        assertThat(guardado.getEstado()).isEqualTo("Activo");
        assertThat(guardado.getRol().getNombre()).isEqualTo("TURISTA_PUBLICO");

        // La contrasena en claro no puede acabar en la tabla usuario.
        assertThat(guardado.getContrasenia()).isEqualTo("$2a$10$hashSimulado");
        assertThat(guardado.getContrasenia()).isNotEqualTo("Turista2026");

        verify(auditoriaService).registrarAuditoria(
                eq("SISTEMA"), eq("INSERT"), eq("usuario"), isNull(), anyString());
        assertThat(resultado).isNotNull();
    }

    @Test
    @DisplayName("El correo se guarda en minusculas y sin espacios: una sola cuenta por direccion")
    void registrarTurista_normalizaElCorreo() {
        dto.setEmail("  Ana.Perez@Correo.PE ");
        when(usuarioRepository.findByNombreUsuario("ana_turista")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana.perez@correo.pe")).thenReturn(Optional.empty());
        when(rolRepository.findByNombre("TURISTA_PUBLICO")).thenReturn(Optional.of(rolTurista));
        when(passwordEncoder.encode("Turista2026")).thenReturn("$2a$10$hashSimulado");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        Usuario guardado = usuarioService.registrarTurista(dto);

        assertThat(guardado.getEmail()).isEqualTo("ana.perez@correo.pe");
        verify(usuarioRepository).findByEmail("ana.perez@correo.pe");
    }

    @Test
    @DisplayName("Contrasena de mas de 72 bytes: rechaza con mensaje propio, sin llamar al codificador")
    void registrarTurista_contraseniaSuperaLimiteBcrypt_rechaza() {
        // 30 caracteres de 3 bytes (UTF-8) superan los 72 bytes aunque no los 72 caracteres.
        String larga = "€".repeat(30);
        dto.setContrasenia(larga);
        dto.setConfirmarContrasenia(larga);

        assertThatThrownBy(() -> usuarioService.registrarTurista(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado larga");

        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Las contrasenas no coinciden: rechaza sin consultar el repositorio")
    void registrarTurista_contrasenasDistintas_rechaza() {
        dto.setConfirmarContrasenia("OtraClave999");

        assertThatThrownBy(() -> usuarioService.registrarTurista(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Las contraseñas no coinciden");

        verify(usuarioRepository, never()).save(any());
        verify(auditoriaService, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Nombre de usuario ya en uso: rechaza y no persiste")
    void registrarTurista_usuarioRepetido_rechaza() {
        when(usuarioRepository.findByNombreUsuario("ana_turista")).thenReturn(Optional.of(new Usuario()));

        assertThatThrownBy(() -> usuarioService.registrarTurista(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El nombre de usuario ya está en uso");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Correo ya registrado: rechaza y no persiste")
    void registrarTurista_emailRepetido_rechaza() {
        when(usuarioRepository.findByNombreUsuario("ana_turista")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana.perez@correo.pe")).thenReturn(Optional.of(new Usuario()));

        assertThatThrownBy(() -> usuarioService.registrarTurista(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El correo electrónico ya está registrado");

        verify(usuarioRepository, never()).save(any());
    }

    @Test
    @DisplayName("Sin el rol TURISTA_PUBLICO sembrado: falla antes de crear la cuenta")
    void registrarTurista_sinRolSembrado_falla() {
        when(usuarioRepository.findByNombreUsuario("ana_turista")).thenReturn(Optional.empty());
        when(usuarioRepository.findByEmail("ana.perez@correo.pe")).thenReturn(Optional.empty());
        when(rolRepository.findByNombre("TURISTA_PUBLICO")).thenReturn(Optional.empty());

        // Es un fallo de configuracion de la base, no un dato malo del
        // formulario: por eso no es IllegalArgumentException, que el
        // controlador mostraria como error del usuario.
        assertThatThrownBy(() -> usuarioService.registrarTurista(dto))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURISTA_PUBLICO");

        verify(usuarioRepository, never()).save(any());
    }
}
