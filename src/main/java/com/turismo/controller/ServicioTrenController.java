package com.turismo.controller;

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

/** RF-12: mantenimiento de horarios y precios de los servicios de tren (admin PeruRail/MTC). */
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

    @PostMapping
    public String guardar(@Valid @ModelAttribute("servicioTren") ServicioTren servicioTren,
                           BindingResult errores, Model model) {
        if (errores.hasErrors()) {
            return listar(model);
        }
        servicioTrenService.guardar(servicioTren, auditoriaService.usuarioActual());
        return "redirect:/servicios-tren";
    }
    @PostMapping("/{id}/eliminar")
    public String eliminar(@PathVariable Integer id) {
        servicioTrenService.eliminar(id, auditoriaService.usuarioActual());
        return "redirect:/servicios-tren?exito=Servicio+eliminado";
    }
}
