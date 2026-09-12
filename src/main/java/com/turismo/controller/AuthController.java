package com.turismo.controller;

import com.turismo.dto.RegistroUsuarioDTO;
import com.turismo.service.UsuarioService;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * HURF06: pantallas de acceso. El cierre de sesion lo atiende el
 * LogoutFilter de Spring Security (POST /logout, configurado en
 * SecurityConfig), que invalida la sesion y borra la cookie; por eso aqui
 * no hay un mapeo propio de /logout.
 *
 * El auto-registro (GET/POST /registro) solo crea cuentas de turista, para
 * que puedan conservar su historial en /mis-informes (RF-10 / CU-09).
 */
@Controller
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    /**
     * Acepta tambien POST porque AccesoDenegadoHandler reenvia (forward) la
     * peticion original tal cual: si esa peticion era el envio de un
     * formulario con token CSRF caducado, llega aqui como POST y con solo GET
     * respondia un 405 sin vista.
     */
    @RequestMapping(value = "/acceso-denegado", method = {RequestMethod.GET, RequestMethod.POST})
    public String accesoDenegado() {
        return "auth/acceso-denegado";
    }

    /** Espacios al inicio o al final ("  ana@correo.pe ") no son parte del dato. */
    @InitBinder("registroDto")
    public void recortarTexto(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping("/registro")
    public String formularioRegistro(Model model) {
        model.addAttribute("registroDto", new RegistroUsuarioDTO());
        return "auth/registro";
    }

    /**
     * Las reglas que dependen de la base (usuario o correo ya registrados) y
     * la comparacion de las dos contrasenas viven en UsuarioService y llegan
     * aqui como IllegalArgumentException. Se traducen a un mensaje en la
     * misma pantalla en vez de dejarlas subir a GlobalExceptionHandler, que
     * mostraria la pagina de error y haria perder el formulario entero.
     */
    @PostMapping("/registro")
    public String registrar(@Valid @ModelAttribute("registroDto") RegistroUsuarioDTO registroDto,
                            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/registro";
        }
        try {
            usuarioService.registrarTurista(registroDto);
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorNegocio", e.getMessage());
            return "auth/registro";
        }
        return "redirect:/login?registrado=true";
    }
}
