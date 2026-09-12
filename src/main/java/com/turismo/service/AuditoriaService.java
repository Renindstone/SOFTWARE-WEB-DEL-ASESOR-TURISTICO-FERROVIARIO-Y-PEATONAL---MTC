package com.turismo.service;

import com.turismo.model.AuditoriaLog;
import com.turismo.repository.AuditoriaLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * RF-16/RNF-07: registra en AuditoriaLog cada operacion administrativa
 * (INSERT/UPDATE/DELETE) y cada sincronizacion externa (SYNC).
 * Caja Blanca: CB-07 (persistencia de valor anterior/nuevo sin nulos).
 */
@Service
public class AuditoriaService {

    /** Usuario que se registra en las sincronizaciones automaticas (6.4). */
    public static final String USUARIO_SISTEMA = "SISTEMA";

    /** Longitud de AudValorAnterior / AudValorNuevo en el diccionario de datos. */
    private static final int MAX_VALOR = 500;

    private final AuditoriaLogRepository auditoriaLogRepository;

    public AuditoriaService(AuditoriaLogRepository auditoriaLogRepository) {
        this.auditoriaLogRepository = auditoriaLogRepository;
    }

    /**
     * CB-07: registra la operacion con sus valores anterior y nuevo.
     *
     * Se une a la transaccion de la operacion auditada (propagacion por
     * defecto) en vez de abrir una propia: asi la traza y el cambio se
     * confirman juntos -- un registro de auditoria de una operacion que
     * termino revirtiendose seria falso (RNF-07) -- y cada peticion usa una
     * sola conexion del pool, que es lo que sostiene el RNF-01 cuando varios
     * turistas generan informes a la vez.
     */
    @Transactional
    public AuditoriaLog registrarAuditoria(String usuario, String operacion, String tablaAfectada,
                                            String valorAnterior, String valorNuevo) {
        AuditoriaLog log = new AuditoriaLog();
        log.setFecha(LocalDateTime.now());
        log.setUsuario(usuario == null || usuario.isBlank() ? USUARIO_SISTEMA : recortar(usuario, 50));
        log.setOperacion(operacion);
        log.setTablaAfectada(tablaAfectada);
        log.setValorAnterior(recortar(valorAnterior, MAX_VALOR));
        log.setValorNuevo(recortar(valorNuevo, MAX_VALOR));
        return auditoriaLogRepository.save(log);
    }

    /** Atajo para las tareas programadas de PeruRail y SENAMHI (RF-14/RF-15). */
    public AuditoriaLog registrarSincronizacion(String tablaAfectada, String detalle) {
        return registrarAuditoria(USUARIO_SISTEMA, "SYNC", tablaAfectada, null, detalle);
    }

    /**
     * Nombre del usuario autenticado, o "ANONIMO" cuando la operacion llega
     * desde el panel publico del turista (InfIdUsuario NULL en 6.4).
     */
    public String usuarioActual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()
                || "anonymousUser".equals(autenticacion.getPrincipal())) {
            return "ANONIMO";
        }
        return autenticacion.getName();
    }

    public List<AuditoriaLog> listarPorTabla(String tablaAfectada) {
        return auditoriaLogRepository.findByTablaAfectada(tablaAfectada);
    }

    public List<AuditoriaLog> listarTodo() {
        return auditoriaLogRepository.findAllByOrderByFechaDescIdDesc();
    }

    private String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() > maximo ? valor.substring(0, maximo) : valor;
    }
}
