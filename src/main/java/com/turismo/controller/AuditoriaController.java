package com.turismo.controller;

import com.turismo.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** RF-16/RNF-07: panel de auditoria para gestores del MTC (ADMIN_MTC). */
@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public String listar(@RequestParam(required = false) String tabla, Model model) {
        model.addAttribute("registros", tabla == null || tabla.isBlank()
                ? auditoriaService.listarTodo()
                : auditoriaService.listarPorTabla(tabla));
        model.addAttribute("tablaFiltrada", tabla);
        return "admin/auditoria";
    }
}
