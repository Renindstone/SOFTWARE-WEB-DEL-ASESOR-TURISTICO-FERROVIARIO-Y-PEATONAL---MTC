package com.turismo.controller;

import com.turismo.dto.PreferenciaDTO;
import com.turismo.dto.ZonaResultadoDTO;
import com.turismo.repository.TipoTurismoRepository;
import com.turismo.service.EstacionService;
import com.turismo.service.PreferenciaService;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * RF-01/RF-02/RF-03 (CU-01, CU-02): buscador de zonas turisticas del panel
 * del turista.
 *
 * Filtros y resultados viven en la misma pagina y viajan por GET: entrar sin
 * parametros muestra el catalogo completo, y cada filtro que se marca recorta
 * el listado sin perder de vista lo que ya estaba. Que los criterios vayan en
 * la URL permite ademas compartir o guardar una busqueda concreta.
 */
@Controller
@RequestMapping("/preferencias")
public class PreferenciaController {

    private final PreferenciaService preferenciaService;
    private final EstacionService estacionService;
    private final TipoTurismoRepository tipoTurismoRepository;

    public PreferenciaController(PreferenciaService preferenciaService,
                                  EstacionService estacionService,
                                  TipoTurismoRepository tipoTurismoRepository) {
        this.preferenciaService = preferenciaService;
        this.estacionService = estacionService;
        this.tipoTurismoRepository = tipoTurismoRepository;
    }

    /**
     * CN-01/CN-05: un criterio mal formado (un tiempo de cero o negativo)
     * devuelve la pagina con el aviso y sin listado, conservando el resto de
     * lo que el turista ya habia marcado.
     */
    @GetMapping
    public String buscar(@Valid @ModelAttribute("preferencia") PreferenciaDTO preferencia,
                          BindingResult errores, Model model) {
        cargarCatalogos(model);
        if (errores.hasErrors()) {
            return "cliente/preferencias";
        }

        List<ZonaResultadoDTO> zonas = preferenciaService.buscarZonasRecomendadas(preferencia);
        model.addAttribute("zonas", zonas);
        model.addAttribute("hayBusqueda", Boolean.TRUE);
        return "cliente/preferencias";
    }

    /** RF-02: solo estaciones activas; RNF-06: tipos de turismo desde la tabla parametrica. */
    private void cargarCatalogos(Model model) {
        model.addAttribute("estaciones", estacionService.listarActivas());
        model.addAttribute("tipos", tipoTurismoRepository.findAllByOrderByNombreAsc());
        model.addAttribute("dificultades", preferenciaService.listarDificultades());
    }
}
