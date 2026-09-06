package com.turismo.service;

import com.turismo.exception.AforoCompletoException;
import com.turismo.model.ControlAforo;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.ControlAforoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caja Blanca: CB-08 (validarAforoDisponible - cupo disponible, se
 * incrementa el contador) y CB-09 (validarAforoDisponible - cupo
 * agotado -> excepcion, sin incrementar el contador).
 *
 * El incremento se hace con un UPDATE atomico que lleva el limite en su
 * propio WHERE (RNF-08), por lo que "hay cupo" y "no hay cupo" se
 * distinguen por el numero de filas que el UPDATE afecta.
 */
@ExtendWith(MockitoExtension.class)
class AforoServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 8, 30);

    @Mock
    private ControlAforoRepository controlAforoRepository;

    @InjectMocks
    private AforoService aforoService;

    private ZonaTuristica crearZona(Integer cupoMaximo) {
        ZonaTuristica zona = new ZonaTuristica();
        zona.setId(1);
        zona.setNombre("Fortaleza de Ollantaytambo");
        zona.setCupoMaximoDiario(cupoMaximo);
        return zona;
    }

    private ControlAforo crearContador(ZonaTuristica zona, int cupoUtilizado) {
        ControlAforo control = new ControlAforo();
        control.setZona(zona);
        control.setFecha(FECHA);
        control.setCupoUtilizado(cupoUtilizado);
        return control;
    }

    /** CB-08: AfoCupoUtilizado (320) menor al maximo (500) -> incrementa y continua. */
    @Test
    void cb08_incrementaElContadorCuandoElCupoUtilizadoEsMenorAlMaximo() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA))
                .thenReturn(Optional.of(crearContador(zona, 320)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 1, 500)).thenReturn(1);

        boolean resultado = aforoService.validarAforoDisponible(zona, FECHA);

        assertThat(resultado).isTrue();
        verify(controlAforoRepository).incrementarCupoUtilizado(1, FECHA, 1, 500);
    }

    /** CB-09: AfoCupoUtilizado ya en el maximo -> el UPDATE no afecta filas. */
    @Test
    void cb09_rechazaLaOperacionCuandoElCupoYaFueAlcanzado() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(eq(1), any(LocalDate.class)))
                .thenReturn(Optional.of(crearContador(zona, 500)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 1, 500)).thenReturn(0);

        assertThatThrownBy(() -> aforoService.validarAforoDisponible(zona, FECHA))
                .isInstanceOf(AforoCompletoException.class)
                .hasMessageContaining("aforo");
    }

    /** CU-08: el rechazo incluye la primera fecha posterior con cupo libre. */
    @Test
    void cb09_sugiereUnaFechaAlternativaCuandoElAforoEstaCompleto() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA))
                .thenReturn(Optional.of(crearContador(zona, 500)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 1, 500)).thenReturn(0);
        // Sin contador para el día siguiente: cupo utilizado = 0, hay lugar.
        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA.plusDays(1)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aforoService.validarAforoDisponible(zona, FECHA))
                .isInstanceOf(AforoCompletoException.class)
                .hasMessageContaining(FECHA.plusDays(1).toString());
    }

    /** RF-16: sin ZonCupoMaximoDiario configurado, la validacion no aplica. */
    @Test
    void noAplicaLaValidacionCuandoLaZonaNoControlaAforo() {
        ZonaTuristica zona = crearZona(null);

        assertThatCode(() -> aforoService.validarAforoDisponible(zona, FECHA))
                .doesNotThrowAnyException();

        verify(controlAforoRepository, never()).incrementarCupoUtilizado(any(), any(), any(), any());
    }

    /** RF-18: una familia de tres descuenta tres cupos, no uno. */
    @Test
    void descuentaUnCupoPorCadaPersonaDelGrupo() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA))
                .thenReturn(Optional.of(crearContador(zona, 320)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 3, 500)).thenReturn(1);

        assertThat(aforoService.validarAforoDisponible(zona, FECHA, 3)).isTrue();

        verify(controlAforoRepository).incrementarCupoUtilizado(1, FECHA, 3, 500);
    }

    /**
     * Un grupo entra entero o no entra: con 2 cupos libres y 3 personas, el
     * UPDATE no afecta filas y el mensaje lo explica.
     */
    @Test
    void rechazaElGrupoQueNoCabeCompletoAunqueQuedenCupos() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(eq(1), any(LocalDate.class)))
                .thenReturn(Optional.of(crearContador(zona, 498)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 3, 500)).thenReturn(0);

        assertThatThrownBy(() -> aforoService.validarAforoDisponible(zona, FECHA, 3))
                .isInstanceOf(AforoCompletoException.class)
                .hasMessageContaining("2 cupo(s) libre(s)")
                .hasMessageContaining("grupo es de 3 personas");
    }

    /** La fecha alternativa debe tener sitio para todo el grupo, no para uno. */
    @Test
    void sugiereUnaFechaConSitioParaTodoElGrupo() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA))
                .thenReturn(Optional.of(crearContador(zona, 500)));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 3, 500)).thenReturn(0);
        // Al dia siguiente solo quedan 2 cupos: no sirve para un grupo de 3.
        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA.plusDays(1)))
                .thenReturn(Optional.of(crearContador(zona, 498)));
        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA.plusDays(2)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aforoService.validarAforoDisponible(zona, FECHA, 3))
                .isInstanceOf(AforoCompletoException.class)
                .hasMessageContaining(FECHA.plusDays(2).toString());
    }

    /** El contador del dia se crea la primera vez que se consulta esa fecha. */
    @Test
    void creaElContadorDelDiaCuandoTodaviaNoExiste() {
        ZonaTuristica zona = crearZona(500);

        when(controlAforoRepository.findByZona_IdAndFecha(1, FECHA)).thenReturn(Optional.empty());
        when(controlAforoRepository.saveAndFlush(any(ControlAforo.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(controlAforoRepository.incrementarCupoUtilizado(1, FECHA, 1, 500)).thenReturn(1);

        assertThat(aforoService.validarAforoDisponible(zona, FECHA)).isTrue();

        verify(controlAforoRepository).saveAndFlush(any(ControlAforo.class));
    }
}
