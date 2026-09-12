package com.turismo.controller;

import com.turismo.exception.ServicioTrenInvalidoException;
import com.turismo.exception.TarifaInvalidaException;
import com.turismo.model.ServicioTren;
import com.turismo.service.AuditoriaService;
import com.turismo.service.EstacionService;
import com.turismo.service.ServicioTrenService;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** RF-13: mantenimiento de horarios y precios de los servicios de tren (admin PeruRail/MTC). */
@Controller
@RequestMapping("/servicios-tren")
public class ServicioTrenController {

    private final ServicioTrenService servicioTrenService;
    private final EstacionService estacionService;
    private final AuditoriaService auditoriaService;

    public ServicioTrenController(ServicioTrenService servicioTrenService,
                                   EstacionService estacionService,
                                   AuditoriaService auditoriaService) {
        this.servicioTrenService = servicioTrenService;
        this.estacionService = estacionService;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("servicios", servicioTrenService.listarTodos());
        if (!model.containsAttribute("servicioTren")) {
            model.addAttribute("servicioTren", new ServicioTren());
        }
        model.addAttribute("estaciones", estacionService.listarTodas());
        return "admin/servicios-tren";
    }

    /** Carga un servicio existente en el formulario para editar su horario o tarifa. */
    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Integer id, Model model) {
        model.addAttribute("servicioTren", servicioTrenService.buscarPorId(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio de tren no encontrado: " + id)));
        return listar(model);
    }

    /**
     * Las reglas de negocio viven en el servicio, pero sus excepciones se
     * traducen aqui a errores del formulario: asi el administrador vuelve a la
     * pantalla con lo que habia tecleado y el mensaje al lado, en vez de
     * perderlo todo en la pagina de error de GlobalExceptionHandler.
     */
    @PostMapping
    public String guardar(@Valid @ModelAttribute("servicioTren") ServicioTren servicioTren,
                           BindingResult errores, Model model, RedirectAttributes redirect) {
        if (errores.hasErrors()) {
            return listar(model);
        }
        try {
            servicioTrenService.guardar(servicioTren, auditoriaService.usuarioActual());
        } catch (TarifaInvalidaException | ServicioTrenInvalidoException ex) {
            errores.reject("servicioTren.invalido", ex.getMessage());
            return listar(model);
        }
        redirect.addFlashAttribute("exito", "Servicio de tren guardado y registrado en auditoría.");
        return "redirect:/servicios-tren";
    }

    /** RF-13/CN-14: baja fisica del servicio, con su traza en AuditoriaLog. */
    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Integer id, RedirectAttributes redirect) {
        boolean eliminado = servicioTrenService.eliminar(id, auditoriaService.usuarioActual());
        if (eliminado) {
            redirect.addFlashAttribute("exito", "Servicio de tren eliminado y registrado en auditoría.");
        } else {
            redirect.addFlashAttribute("error",
                    "El servicio de tren ya no existe: no se eliminó nada.");
        }
        return "redirect:/servicios-tren";
    }
}
