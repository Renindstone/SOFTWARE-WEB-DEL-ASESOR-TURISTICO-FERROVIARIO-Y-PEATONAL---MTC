package com.turismo.controller;

import com.turismo.repository.AuditoriaLogRepository;
import com.turismo.repository.EstacionRepository;
import com.turismo.repository.InformePlanificacionRepository;
import com.turismo.repository.ZonaTuristicaRepository;
import com.turismo.service.ZonaTuristicaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El panel general resume la actividad interna del sistema, asi que es una
 * pantalla de gestion (RNF-05). El turista, entre autenticado como
 * TURISTA_PUBLICO o de forma anonima, va directo a sus preferencias (CU-01).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HomeControllerTest {

    @Mock
    private ZonaTuristicaRepository zonaTuristicaRepository;
    @Mock
    private EstacionRepository estacionRepository;
    @Mock
    private InformePlanificacionRepository informePlanificacionRepository;
    @Mock
    private AuditoriaLogRepository auditoriaLogRepository;
    @Mock
    private ZonaTuristicaService zonaTuristicaService;

    @InjectMocks
    private HomeController homeController;

    private Authentication conRol(String rol) {
        return new UsernamePasswordAuthenticationToken("usuario", "clave",
                List.of(new SimpleGrantedAuthority(rol)));
    }

    private Authentication anonimo() {
        return new AnonymousAuthenticationToken("clave", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
    }

    private void prepararCatalogo() {
        when(zonaTuristicaRepository.findByEstado(anyString())).thenReturn(List.of());
        when(estacionRepository.findByEstado(anyString())).thenReturn(List.of());
        when(informePlanificacionRepository.count()).thenReturn(0L);
        when(auditoriaLogRepository.count()).thenReturn(0L);
        when(zonaTuristicaService.listarActivasConEstacionYPreferencias()).thenReturn(List.of());
    }

    /** Sin sesion iniciada no se muestra el panel general. */
    @Test
    void redirigeAPreferenciasAlVisitanteAnonimo() {
        Model model = new ConcurrentModel();

        assertThat(homeController.inicio(anonimo(), model)).isEqualTo("redirect:/preferencias");
        assertThat(homeController.inicio(null, model)).isEqualTo("redirect:/preferencias");

        // Ni siquiera se consultan los contadores de gestion.
        verify(zonaTuristicaService, never()).listarActivasConEstacionYPreferencias();
    }

    /** Un turista registrado tampoco ve el panel de gestion. */
    @Test
    void redirigeAPreferenciasAlTuristaPublico() {
        Model model = new ConcurrentModel();

        String vista = homeController.inicio(conRol("ROLE_TURISTA_PUBLICO"), model);

        assertThat(vista).isEqualTo("redirect:/preferencias");
        verify(auditoriaLogRepository, never()).count();
    }

    /** ADMIN_MTC, TRAVEL_GROUP_USER y PERURAIL_ADMIN si acceden al panel. */
    @Test
    void muestraElPanelAlPersonalAutorizado() {
        prepararCatalogo();

        for (String rol : List.of("ROLE_ADMIN_MTC", "ROLE_TRAVEL_GROUP_USER", "ROLE_PERURAIL_ADMIN")) {
            Model model = new ConcurrentModel();

            assertThat(homeController.inicio(conRol(rol), model)).isEqualTo("dashboard");
            assertThat(model.getAttribute("totalZonas")).isNotNull();
            assertThat(model.getAttribute("zonasRecientes")).isNotNull();
        }
    }
}
