package com.turismo.service;

import com.turismo.exception.AforoCompletoException;
import com.turismo.model.ControlAforo;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.ControlAforoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * RF-16/RF-17/RNF-08: valida y actualiza de forma atomica el cupo maximo
 * diario de una zona turistica antes de confirmar el informe de visita.
 * Caja Blanca: CB-08 (cupo disponible), CB-09 (cupo agotado -> excepcion).
 */
@Service
public class AforoService {

    /** Dias hacia adelante que se exploran al sugerir una fecha alternativa (CU-08). */
    private static final int DIAS_SUGERENCIA_ALTERNATIVA = 30;

    private final ControlAforoRepository controlAforoRepository;

    public AforoService(ControlAforoRepository controlAforoRepository) {
        this.controlAforoRepository = controlAforoRepository;
    }

    /**
     * CB-08/CB-09: si la zona no tiene ZonCupoMaximoDiario configurado, no
     * aplica la validacion (RF-16). Si lo tiene, descuenta del contador
     * tantos cupos como personas viajen, de forma atomica; si no quedan
     * suficientes, rechaza la operacion con AforoCompletoException,
     * incluyendo en el mensaje una fecha alternativa con sitio para todo el
     * grupo.
     *
     * El incremento se delega en un UPDATE ... SET AfoCupoUtilizado =
     * AfoCupoUtilizado + n con el limite verificado en el mismo WHERE
     * (RNF-08 / seccion 6.3), en lugar de leer, sumar y volver a guardar
     * desde la aplicacion, que dejaria una ventana de condicion de carrera.
     */
    @Transactional
    public boolean validarAforoDisponible(ZonaTuristica zona, LocalDate fecha, int personas) {
        if (personas <= 0) {
            throw new IllegalArgumentException("El grupo debe tener al menos una persona");
        }
        Integer cupoMaximo = zona.getCupoMaximoDiario();
        if (cupoMaximo == null) {
            return true;
        }

        asegurarContador(zona, fecha);

        int filasActualizadas = controlAforoRepository
                .incrementarCupoUtilizado(zona.getId(), fecha, personas, cupoMaximo);

        if (filasActualizadas == 0) {
            throw new AforoCompletoException(
                    mensajeDeRechazo(zona, fecha, personas)
                            + sugerirFechaAlternativa(zona, fecha, personas));
        }
        return true;
    }

    /** Con una sola persona, RF-16 en su forma original. */
    @Transactional
    public boolean validarAforoDisponible(ZonaTuristica zona, LocalDate fecha) {
        return validarAforoDisponible(zona, fecha, 1);
    }

    /**
     * Un grupo puede ser rechazado aunque queden cupos, si no alcanzan para
     * todos; el mensaje lo distingue para que el turista entienda por que.
     */
    private String mensajeDeRechazo(ZonaTuristica zona, LocalDate fecha, int personas) {
        int disponibles = consultarCupoDisponible(zona, fecha).orElse(0);
        if (personas > 1 && disponibles > 0) {
            return "El aforo de la zona para la fecha seleccionada solo tiene " + disponibles
                    + " cupo(s) libre(s) y el grupo es de " + personas + " personas";
        }
        return "El aforo de la zona para la fecha seleccionada ya fue alcanzado";
    }

    /**
     * Crea el contador del dia si aun no existe. La restriccion UNIQUE
     * (AfoIdZona, AfoFecha) garantiza que, si dos peticiones simultaneas
     * intentan crearlo, solo una lo consiga; la otra reutiliza el existente.
     */
    private void asegurarContador(ZonaTuristica zona, LocalDate fecha) {
        if (controlAforoRepository.findByZona_IdAndFecha(zona.getId(), fecha).isPresent()) {
            return;
        }
        ControlAforo nuevo = new ControlAforo();
        nuevo.setZona(zona);
        nuevo.setFecha(fecha);
        nuevo.setCupoUtilizado(0);
        try {
            controlAforoRepository.saveAndFlush(nuevo);
        } catch (DataIntegrityViolationException ex) {
            // Otro hilo lo creo primero: el contador ya existe y sirve igual.
        }
    }

    /**
     * CU-08: primera fecha posterior con cupo libre. Se limita la busqueda a
     * DIAS_SUGERENCIA_ALTERNATIVA dias; si no encuentra ninguna, devuelve
     * cadena vacia y el mensaje queda solo con el rechazo.
     */
    private String sugerirFechaAlternativa(ZonaTuristica zona, LocalDate fecha, int personas) {
        return buscarFechaAlternativa(zona, fecha, personas)
                .map(alternativa -> ". Fecha alternativa sugerida: " + alternativa)
                .orElse("");
    }

    /**
     * CU-08: primera fecha posterior con sitio para TODO el grupo. No sirve
     * proponer un dia en el que solo cabrian algunos.
     */
    @Transactional(readOnly = true)
    public Optional<LocalDate> buscarFechaAlternativa(ZonaTuristica zona, LocalDate fecha, int personas) {
        Integer cupoMaximo = zona.getCupoMaximoDiario();
        if (cupoMaximo == null) {
            return Optional.empty();
        }
        for (int dia = 1; dia <= DIAS_SUGERENCIA_ALTERNATIVA; dia++) {
            LocalDate candidata = fecha.plusDays(dia);
            int usado = controlAforoRepository.findByZona_IdAndFecha(zona.getId(), candidata)
                    .map(ControlAforo::getCupoUtilizado)
                    .orElse(0);
            if (usado + personas <= cupoMaximo) {
                return Optional.of(candidata);
            }
        }
        return Optional.empty();
    }

    /** Expuesto para que la vista pueda ofrecer la fecha alternativa (CN-09). */
    @Transactional(readOnly = true)
    public Optional<LocalDate> buscarFechaAlternativa(ZonaTuristica zona, LocalDate fecha) {
        return buscarFechaAlternativa(zona, fecha, 1);
    }

    /** Cupo restante de la zona para la fecha, o vacio si la zona no controla aforo. */
    @Transactional(readOnly = true)
    public Optional<Integer> consultarCupoDisponible(ZonaTuristica zona, LocalDate fecha) {
        Integer cupoMaximo = zona.getCupoMaximoDiario();
        if (cupoMaximo == null) {
            return Optional.empty();
        }
        int usado = controlAforoRepository.findByZona_IdAndFecha(zona.getId(), fecha)
                .map(ControlAforo::getCupoUtilizado)
                .orElse(0);
        return Optional.of(Math.max(cupoMaximo - usado, 0));
    }
}
