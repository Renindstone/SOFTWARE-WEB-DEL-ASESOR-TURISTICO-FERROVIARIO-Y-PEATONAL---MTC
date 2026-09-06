package com.turismo.controller;

import com.turismo.service.EstacionService;
import com.turismo.service.ZonaTuristicaService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * RF-11 (CU-05): consulta de estaciones en modo solo lectura para Travel
 * Group Perú, y RF-09: listado de estaciones con sus zonas asignadas.
 */
@Controller
@RequestMapping("/estaciones")
public class EstacionController {

    private final EstacionService estacionService;
    private final ZonaTuristicaService zonaTuristicaService;

    public EstacionController(EstacionService estacionService, ZonaTuristicaService zonaTuristicaService) {
        this.estacionService = estacionService;
        this.zonaTuristicaService = zonaTuristicaService;
    }

    /**
     * CN-06: listado de estaciones activas, sin acciones de edicion ni de
     * eliminacion. Con incluirInactivas=true se muestran tambien las dadas de
     * baja, para que Travel Group Perú pueda entender por que una estacion ya
     * no aparece en el selector del turista.
     */
    @GetMapping
    public String listar(@RequestParam(defaultValue = "false") boolean incluirInactivas, Model model) {
        model.addAttribute("estaciones",
                incluirInactivas ? estacionService.listarTodas() : estacionService.listarActivas());
        model.addAttribute("incluirInactivas", incluirInactivas);
        return "admin/estaciones-lista";
    }

    /** RF-09: estaciones con las zonas turisticas que tienen asignadas. */
    @GetMapping("/zonas-asignadas")
    public String listarConZonas(Model model) {
        model.addAttribute("zonas", zonaTuristicaService.listarActivasConEstacionYPreferencias());
        return "admin/estaciones-zonas";
    }

    /** RF-02 (CU-02): selector publico de estacion de partida para el turista. */
    @GetMapping("/seleccion")
    public String seleccionar(Model model) {
        model.addAttribute("estaciones", estacionService.listarActivas());
        return "cliente/seleccion-estacion";
    }
}
