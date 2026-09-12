package com.turismo.controller;

import com.turismo.dto.RutaCalculadaDTO;
import com.turismo.model.Estacion;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.ZonaTuristicaRepository;
import com.turismo.service.AforoService;
import com.turismo.service.ClimaService;
import com.turismo.service.EstacionService;
import com.turismo.service.RutaPeatonalService;
import com.turismo.service.ServicioTrenService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * RF-04/RF-05/RF-06/RF-07 (CU-03): detalle de la ruta peatonal de ida y
 * vuelta calculada por RutaPeatonalService, con el pronostico del SENAMHI y
 * la tarifa del tren para la fecha de visita.
 */
@Controller
public class RutaController {

    private final RutaPeatonalService rutaPeatonalService;
    private final EstacionService estacionService;
    private final ZonaTuristicaRepository zonaTuristicaRepository;
    private final ClimaService climaService;
    private final ServicioTrenService servicioTrenService;
    private final AforoService aforoService;

    public RutaController(RutaPeatonalService rutaPeatonalService,
                           EstacionService estacionService,
                           ZonaTuristicaRepository zonaTuristicaRepository,
                           ClimaService climaService,
                           ServicioTrenService servicioTrenService,
                           AforoService aforoService) {
        this.rutaPeatonalService = rutaPeatonalService;
        this.estacionService = estacionService;
        this.zonaTuristicaRepository = zonaTuristicaRepository;
        this.climaService = climaService;
        this.servicioTrenService = servicioTrenService;
        this.aforoService = aforoService;
    }

    /**
     * CU-03: muestra distancia, tiempo, dificultad, clima y tarifa. Calcular
     * la ruta NO consume aforo: el cupo se descuenta recien al generar el
     * informe consolidado (CU-08).
     */
    @GetMapping("/rutas/detalle")
    public String detalle(@RequestParam Integer idEstacion,
                           @RequestParam Integer idZona,
                           @RequestParam(required = false)
                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVisita,
                           Model model) {
        Estacion origen = estacionService.buscarActivaPorId(idEstacion);
        ZonaTuristica destino = zonaTuristicaRepository.findById(idZona)
                .orElseThrow(() -> new IllegalArgumentException("Zona turística no encontrada: " + idZona));
        // RF-10: una zona dada de baja desaparece del buscador, pero su
        // direccion sigue existiendo; no debe poder planificarse por ella.
        if (!"Activa".equalsIgnoreCase(destino.getEstado())) {
            throw new IllegalArgumentException(
                    "La zona turística " + destino.getNombre() + " no está disponible actualmente");
        }
        LocalDate fecha = fechaVisita == null ? LocalDate.now() : fechaVisita;

        RutaCalculadaDTO ruta = rutaPeatonalService.calcularRutaPeatonalIdaVuelta(origen, destino);

        model.addAttribute("ruta", ruta);
        model.addAttribute("origen", origen);
        model.addAttribute("destino", destino);
        model.addAttribute("fechaVisita", fecha);
        model.addAttribute("clima", climaService.buscarPorEstacionYFecha(origen.getId(), fecha).orElse(null));
        model.addAttribute("servicios", servicioTrenService.listarHaciaEstacion(origen.getId()));
        model.addAttribute("cupoDisponible",
                aforoService.consultarCupoDisponible(destino, fecha).orElse(null));
        return "cliente/ruta-detalle";
    }
}
