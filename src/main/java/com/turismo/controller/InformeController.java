package com.turismo.controller;

import com.turismo.dto.InformeConsolidadoDTO;
import com.turismo.dto.VisitanteDTO;
import com.turismo.model.Estacion;
import com.turismo.model.ServicioTren;
import com.turismo.model.Usuario;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.ServicioTrenRepository;
import com.turismo.repository.UsuarioRepository;
import com.turismo.repository.ZonaTuristicaRepository;
import com.turismo.service.EstacionService;
import com.turismo.service.InformeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * RF-08 (CU-03/CU-08): genera el informe consolidado de la visita, en vista
 * web y exportacion PDF.
 */
@Controller
public class InformeController {

    private final InformeService informeService;
    private final EstacionService estacionService;
    private final ZonaTuristicaRepository zonaTuristicaRepository;
    private final ServicioTrenRepository servicioTrenRepository;
    private final UsuarioRepository usuarioRepository;

    public InformeController(InformeService informeService,
                              EstacionService estacionService,
                              ZonaTuristicaRepository zonaTuristicaRepository,
                              ServicioTrenRepository servicioTrenRepository,
                              UsuarioRepository usuarioRepository) {
        this.informeService = informeService;
        this.estacionService = estacionService;
        this.zonaTuristicaRepository = zonaTuristicaRepository;
        this.servicioTrenRepository = servicioTrenRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * CN-07/CN-09: emite el informe. Aqui se valida y consume el aforo
     * (AforoService); si la zona ya esta completa para la fecha, la
     * excepcion la traduce GlobalExceptionHandler con la fecha alternativa.
     */
    @GetMapping("/informes/consolidado")
    public String generar(@RequestParam Integer idEstacion,
                           @RequestParam Integer idZona,
                           @RequestParam(required = false) Integer idServicioTren,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVisita,
                           @RequestParam(name = "edades", required = false) List<Integer> edades,
                           Authentication autenticacion,
                           Model model) {
        Estacion origen = estacionService.buscarActivaPorId(idEstacion);
        ZonaTuristica destino = buscarZona(idZona);
        ServicioTren servicio = buscarServicio(idServicioTren);

        InformeConsolidadoDTO informe = informeService.generarInformeConsolidado(
                origen, destino, servicio, fechaVisita, construirGrupo(edades),
                usuarioAutenticado(autenticacion));

        model.addAttribute("informe", informe);
        model.addAttribute("idEstacion", idEstacion);
        model.addAttribute("idZona", idZona);
        model.addAttribute("idServicioTren", idServicioTren);
        model.addAttribute("fechaVisita", fechaVisita);
        model.addAttribute("edades", edades);
        return "informes/informe-consolidado";
    }

    /**
     * CN-07: descarga del mismo informe en PDF. Reconstruye la vista previa
     * en lugar de emitir uno nuevo, para no descontar el aforo por segunda
     * vez al pulsar "Descargar" sobre un informe ya generado.
     */
    @GetMapping(value = "/informes/consolidado/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> exportarPdf(@RequestParam Integer idEstacion,
                                               @RequestParam Integer idZona,
                                               @RequestParam(required = false) Integer idServicioTren,
                                               @RequestParam(required = false) String codigo,
                                               @RequestParam(name = "edades", required = false) List<Integer> edades,
                                               @RequestParam
                                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVisita) {
        Estacion origen = estacionService.buscarActivaPorId(idEstacion);
        ZonaTuristica destino = buscarZona(idZona);
        ServicioTren servicio = buscarServicio(idServicioTren);

        InformeConsolidadoDTO informe = informeService.previsualizarInforme(
                origen, destino, servicio, fechaVisita, construirGrupo(edades), codigo);
        byte[] pdf = informeService.exportarPdf(informe);

        String nombreArchivo = "informe-" + (informe.getCodigo() == null ? "visita" : informe.getCodigo()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                .body(pdf);
    }

    /**
     * RF-08: version HTML imprimible del informe, sin la navegacion del sitio.
     * Es la otra mitad del "PDF/HTML" del requisito, y como la previsualizacion
     * tampoco vuelve a descontar el aforo.
     */
    @GetMapping("/informes/consolidado/html")
    public String exportarHtml(@RequestParam Integer idEstacion,
                                @RequestParam Integer idZona,
                                @RequestParam(required = false) Integer idServicioTren,
                                @RequestParam(required = false) String codigo,
                                @RequestParam(name = "edades", required = false) List<Integer> edades,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVisita,
                                Model model) {
        Estacion origen = estacionService.buscarActivaPorId(idEstacion);
        ZonaTuristica destino = buscarZona(idZona);
        ServicioTren servicio = buscarServicio(idServicioTren);

        model.addAttribute("informe", informeService.previsualizarInforme(
                origen, destino, servicio, fechaVisita, construirGrupo(edades), codigo));
        return "informes/informe-pdf";
    }

    /**
     * RF-18: el formulario envia una edad por acompanante. InformeService las
     * agrupa despues, de modo que tres personas de 35 anos acaben en una sola
     * fila con cantidad 3 y no en tres filas.
     */
    private List<VisitanteDTO> construirGrupo(List<Integer> edades) {
        List<VisitanteDTO> grupo = new ArrayList<>();
        if (edades == null) {
            return grupo;
        }
        for (Integer edad : edades) {
            if (edad != null) {
                grupo.add(new VisitanteDTO(edad, 1));
            }
        }
        return grupo;
    }

    private ZonaTuristica buscarZona(Integer idZona) {
        return zonaTuristicaRepository.findById(idZona)
                .orElseThrow(() -> new IllegalArgumentException("Zona turística no encontrada: " + idZona));
    }

    private ServicioTren buscarServicio(Integer idServicioTren) {
        return idServicioTren == null ? null : servicioTrenRepository.findById(idServicioTren).orElse(null);
    }

    /** InfIdUsuario queda NULL cuando la consulta es anonima (diccionario 6.4). */
    private Usuario usuarioAutenticado(Authentication autenticacion) {
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(autenticacion.getPrincipal())) {
            return null;
        }
        return usuarioRepository.findByNombreUsuario(autenticacion.getName()).orElse(null);
    }
}
