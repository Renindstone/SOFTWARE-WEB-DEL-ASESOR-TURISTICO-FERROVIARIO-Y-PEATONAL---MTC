package com.turismo.controller;

import com.turismo.model.ZonaTuristica;
import com.turismo.repository.PreferenciaRepository;
import com.turismo.service.AuditoriaService;
import com.turismo.service.EstacionService;
import com.turismo.service.ZonaTuristicaService;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * RF-11/RF-18 (CU-04): CRUD de zonas turisticas para Travel Group Perú, con
 * preferencias y cupo diario.
 */
@Controller
@RequestMapping("/zonas")
public class ZonaTuristicaController {

    private final ZonaTuristicaService zonaTuristicaService;
    private final EstacionService estacionService;
    private final PreferenciaRepository preferenciaRepository;
    private final AuditoriaService auditoriaService;

    public ZonaTuristicaController(ZonaTuristicaService zonaTuristicaService,
            EstacionService estacionService,
            PreferenciaRepository preferenciaRepository,
            AuditoriaService auditoriaService) {
        this.zonaTuristicaService = zonaTuristicaService;
        this.estacionService = estacionService;
        this.preferenciaRepository = preferenciaRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Recorta los espacios de todos los campos de texto antes de validar: un
     * nombre formado solo por espacios pasaba el required del navegador y se
     * guardaba como una zona sin nombre.
     */
    @InitBinder("zona")
    public void recortarTexto(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("zonas", zonaTuristicaService.listarTodasConEstacionYPreferencias());
        model.addAttribute("zona", new ZonaTuristica());
        model.addAttribute("idsPreferencia", List.of());
        cargarCatalogos(model);
        return "admin/zonas-lista";
    }

    /**
     * CU-04: edicion de una zona existente, con sus preferencias ya
     * preseleccionadas.
     */
    @GetMapping("/{id}/editar")
    public String formularioEditar(@PathVariable Integer id, Model model) {
        ZonaTuristica zona = zonaTuristicaService.buscarParaEdicion(id)
                .orElseThrow(() -> new IllegalArgumentException("Zona turística no encontrada: " + id));
        model.addAttribute("zona", zona);
        model.addAttribute("idsPreferencia", zonaTuristicaService.listarIdsPreferencia(id));
        cargarCatalogos(model);
        return "admin/zona-form";
    }

    /**
     * CN-04/CN-05: guarda la zona con sus preferencias. Si falta la preferencia
     * de turismo o la validacion de campos falla, vuelve al formulario con el
     * mensaje en vez de perder lo ya escrito.
     */
    @PostMapping
    public String guardar(@Valid @ModelAttribute("zona") ZonaTuristica zona,
            BindingResult errores,
            @RequestParam(name = "idsPreferencia", required = false) List<Integer> idsPreferencia,
            Model model, RedirectAttributes redirect) {
        List<Integer> preferencias = idsPreferencia == null ? List.of() : idsPreferencia;

        if (preferencias.isEmpty()) {
            errores.rejectValue("preferencias", "preferencias.requeridas",
                    "Debe seleccionar al menos una preferencia");
        }
        if (errores.hasErrors()) {
            model.addAttribute("zonas", zonaTuristicaService.listarTodasConEstacionYPreferencias());
            model.addAttribute("idsPreferencia", preferencias);
            cargarCatalogos(model);
            return "admin/zonas-lista";
        }

        zonaTuristicaService.registrarOActualizar(zona, preferencias, auditoriaService.usuarioActual());
        redirect.addFlashAttribute("exito", "Zona turística guardada y registrada en auditoría.");
        return "redirect:/zonas";
    }

    /** RF-11: baja de la zona turistica (ZonEstado = Inactiva), auditada. */
    @PostMapping("/{id}/inhabilitar")
    public String inhabilitar(@PathVariable Integer id, RedirectAttributes redirect) {
        zonaTuristicaService.inhabilitar(id, auditoriaService.usuarioActual());
        redirect.addFlashAttribute("exito", "Zona turística inhabilitada: ya no se ofrece al turista.");
        return "redirect:/zonas";
    }

    @PostMapping("/{id}/habilitar")
    public String habilitar(@PathVariable Integer id, RedirectAttributes redirect) {
        zonaTuristicaService.habilitar(id, auditoriaService.usuarioActual());
        redirect.addFlashAttribute("exito", "Zona turística habilitada: vuelve a ofrecerse al turista.");
        return "redirect:/zonas";
    }

    private void cargarCatalogos(Model model) {
        model.addAttribute("estaciones", estacionService.listarActivas());
        model.addAttribute("preferencias", preferenciaRepository.findAllByOrderByNombreAsc());
    }
}
