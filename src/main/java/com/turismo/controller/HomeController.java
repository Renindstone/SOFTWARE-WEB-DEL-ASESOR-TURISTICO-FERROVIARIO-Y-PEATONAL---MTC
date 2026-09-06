package com.turismo.controller;

import com.turismo.repository.AuditoriaLogRepository;
import com.turismo.repository.EstacionRepository;
import com.turismo.repository.InformePlanificacionRepository;
import com.turismo.repository.ZonaTuristicaRepository;
import com.turismo.service.ZonaTuristicaService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

/**
 * Pagina de entrada del sistema.
 *
 * El panel general resume el estado del catalogo y de la actividad interna
 * (informes emitidos, eventos de auditoria), asi que es una pantalla de
 * gestion: solo la ven los roles administrativos. El turista -- autenticado
 * como TURISTA_PUBLICO o entrando de forma anonima -- va directo al
 * formulario de preferencias, que es donde empieza su caso de uso (CU-01).
 */
@Controller
public class HomeController {

    private static final int ZONAS_RECIENTES = 5;

    /** Roles con acceso al panel de gestion (RNF-05). */
    private static final Set<String> ROLES_ADMINISTRATIVOS =
            Set.of("ROLE_ADMIN_MTC", "ROLE_TRAVEL_GROUP_USER", "ROLE_PERURAIL_ADMIN");

    private final ZonaTuristicaRepository zonaTuristicaRepository;
    private final EstacionRepository estacionRepository;
    private final InformePlanificacionRepository informePlanificacionRepository;
    private final AuditoriaLogRepository auditoriaLogRepository;
    private final ZonaTuristicaService zonaTuristicaService;

    public HomeController(ZonaTuristicaRepository zonaTuristicaRepository,
                           EstacionRepository estacionRepository,
                           InformePlanificacionRepository informePlanificacionRepository,
                           AuditoriaLogRepository auditoriaLogRepository,
                           ZonaTuristicaService zonaTuristicaService) {
        this.zonaTuristicaRepository = zonaTuristicaRepository;
        this.estacionRepository = estacionRepository;
        this.informePlanificacionRepository = informePlanificacionRepository;
        this.auditoriaLogRepository = auditoriaLogRepository;
        this.zonaTuristicaService = zonaTuristicaService;
    }

    @GetMapping("/")
    public String inicio(Authentication autenticacion, Model model) {
        if (!esPersonalAutorizado(autenticacion)) {
            return "redirect:/preferencias";
        }

        model.addAttribute("totalZonas", zonaTuristicaRepository.findByEstado("Activa").size());
        model.addAttribute("totalEstaciones", estacionRepository.findByEstado("Activa").size());
        model.addAttribute("totalInformes", informePlanificacionRepository.count());
        model.addAttribute("totalAuditorias", auditoriaLogRepository.count());
        model.addAttribute("zonasRecientes", zonaTuristicaService.listarActivasConEstacionYPreferencias()
                .stream().limit(ZONAS_RECIENTES).toList());
        return "dashboard";
    }

    /**
     * Un turista anonimo llega sin Authentication, o con el token anonimo de
     * Spring Security; uno registrado llega con ROLE_TURISTA_PUBLICO. En los
     * tres casos el panel de gestion no le corresponde.
     */
    private boolean esPersonalAutorizado(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(autenticacion.getPrincipal())) {
            return false;
        }
        List<String> roles = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return roles.stream().anyMatch(ROLES_ADMINISTRATIVOS::contains);
    }
}
