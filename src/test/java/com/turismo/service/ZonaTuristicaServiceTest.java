package com.turismo.service;

import com.turismo.model.Estacion;
import com.turismo.model.Preferencia;
import com.turismo.model.ZonaPreferencia;
import com.turismo.model.ZonaTuristica;
import com.turismo.repository.PreferenciaRepository;
import com.turismo.repository.ZonaPreferenciaRepository;
import com.turismo.repository.ZonaTuristicaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RF-11/RF-16/RF-18 (CU-04, CN-04/CN-05/CN-10): CRUD de zonas turisticas de
 * Travel Group Peru, con su validacion de preferencias y el registro de
 * auditoria de cada operacion.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ZonaTuristicaServiceTest {

    @Mock
    private ZonaTuristicaRepository zonaTuristicaRepository;
    @Mock
    private ZonaPreferenciaRepository zonaPreferenciaRepository;
    @Mock
    private PreferenciaRepository preferenciaRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @InjectMocks
    private ZonaTuristicaService zonaTuristicaService;

    private ZonaTuristica zona;

    private static Preferencia crearPreferencia(Integer id, String nombre) {
        Preferencia preferencia = new Preferencia();
        preferencia.setId(id);
        preferencia.setNombre(nombre);
        return preferencia;
    }

    @BeforeEach
    void prepararEscenario() {
        Estacion estacion = new Estacion();
        estacion.setId(3);
        estacion.setNombre("Estacion Urubamba");

        zona = new ZonaTuristica();
        zona.setNombre("Salinas de Maras");
        zona.setLatitud(new BigDecimal("-13.295007"));
        zona.setLongitud(new BigDecimal("-72.116300"));
        zona.setEstacionCercana(estacion);

        when(zonaTuristicaRepository.save(any(ZonaTuristica.class)))
                .thenAnswer(invocacion -> {
                    ZonaTuristica guardada = invocacion.getArgument(0);
                    if (guardada.getId() == null) {
                        guardada.setId(20);
                    }
                    return guardada;
                });
        when(preferenciaRepository.findById(2)).thenReturn(Optional.of(crearPreferencia(2, "Naturaleza")));
        when(preferenciaRepository.findById(1)).thenReturn(Optional.of(crearPreferencia(1, "Historia/Cultura")));
        when(preferenciaRepository.findAllById(any()))
                .thenReturn(List.of(crearPreferencia(2, "Naturaleza")));
        when(zonaPreferenciaRepository.findByZonaTuristica_Id(any())).thenReturn(List.of());
    }

    /** CN-04: alta valida -> se guarda y genera un registro INSERT en la auditoria. */
    @Test
    void cn04_registraLaZonaYGeneraAuditoriaDeAlta() {
        ZonaTuristica guardada = zonaTuristicaService.registrarOActualizar(zona, List.of(2), "travel_ana");

        assertThat(guardada.getId()).isEqualTo(20);
        // ZonEstado toma el valor por defecto del diccionario de datos.
        assertThat(guardada.getEstado()).isEqualTo("Activa");

        ArgumentCaptor<String> operacion = ArgumentCaptor.forClass(String.class);
        verify(auditoriaService).registrarAuditoria(eq("travel_ana"), operacion.capture(),
                eq("zona_turistica"), eq(null), anyString());
        assertThat(operacion.getValue()).isEqualTo("INSERT");
    }

    /** CN-05: sin ninguna preferencia seleccionada, no se guarda el registro. */
    @Test
    void cn05_rechazaLaZonaSinNingunaPreferencia() {
        assertThatThrownBy(() -> zonaTuristicaService.registrarOActualizar(zona, List.of(), "travel_ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("al menos una preferencia");

        verify(zonaTuristicaRepository, never()).save(any());
        verify(auditoriaService, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    /** CU-04: la zona debe quedar asociada a una estacion ferroviaria cercana. */
    @Test
    void rechazaLaZonaSinEstacionCercana() {
        zona.setEstacionCercana(null);

        assertThatThrownBy(() -> zonaTuristicaService.registrarOActualizar(zona, List.of(2), "travel_ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("estación");

        verify(zonaTuristicaRepository, never()).save(any());
    }

    /** CN-10: se persiste una fila de ZonaPreferencia por cada categoria marcada. */
    @Test
    void cn10_guardaUnaFilaPorCadaPreferenciaAsociada() {
        zonaTuristicaService.registrarOActualizar(zona, List.of(1, 2), "travel_ana");

        verify(zonaPreferenciaRepository, times(2)).save(any(ZonaPreferencia.class));
    }

    /**
     * RF-11: la baja es logica (ZonEstado = Inactiva). Borrar la fila
     * chocaria con el ON DELETE RESTRICT de ruta_peatonal, control_aforo e
     * informe_planificacion (seccion 6.3).
     */
    @Test
    void eliminarDejaLaZonaInactivaYRegistraLaAuditoria() {
        zona.setId(20);
        zona.setEstado("Activa");
        when(zonaTuristicaRepository.findById(20)).thenReturn(Optional.of(zona));

        zonaTuristicaService.inhabilitar(20, "travel_ana");

        assertThat(zona.getEstado()).isEqualTo("Inactiva");
        verify(zonaTuristicaRepository, never()).delete(any());
        verify(auditoriaService).registrarAuditoria(eq("travel_ana"), eq("UPDATE"),
                eq("zona_turistica"), anyString(), anyString());
    }

    // ------------------------------------------------------------------
    // RNF-06: la tabla preferencia se amplia desde el modal de zonas, sin
    // tocar el codigo y con la misma traza que cualquier otra alta (RNF-07).
    // ------------------------------------------------------------------

    @Test
    void registrarPreferencia_nueva_laGuardaRecortadaYRegistraLaAuditoria() {
        when(preferenciaRepository.findByNombreIgnoreCase("Astronomía")).thenReturn(Optional.empty());
        when(preferenciaRepository.save(any(Preferencia.class))).thenAnswer(inv -> {
            Preferencia p = inv.getArgument(0);
            p.setId(9);
            return p;
        });

        Preferencia creada = zonaTuristicaService.registrarPreferencia("  Astronomía ", "Cielos del altiplano ", "travel_ana");

        assertThat(creada.getId()).isEqualTo(9);
        assertThat(creada.getNombre()).isEqualTo("Astronomía");
        assertThat(creada.getDescripcion()).isEqualTo("Cielos del altiplano");
        verify(auditoriaService).registrarAuditoria(eq("travel_ana"), eq("INSERT"), eq("preferencia"),
                eq(null), eq("PreNombre=Astronomía; PreDescripcion=Cielos del altiplano"));
    }

    @Test
    void registrarPreferencia_repetidaSinDistinguirMayusculas_rechaza() {
        Preferencia existente = crearPreferencia(3, "Gastronomia");
        when(preferenciaRepository.findByNombreIgnoreCase("GASTRONOMIA")).thenReturn(Optional.of(existente));

        assertThatThrownBy(() -> zonaTuristicaService.registrarPreferencia("GASTRONOMIA", null, "travel_ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya existe la preferencia «Gastronomia»");

        verify(preferenciaRepository, never()).save(any());
        verify(auditoriaService, never()).registrarAuditoria(any(), any(), any(), any(), any());
    }

    @Test
    void registrarPreferencia_nombreVacioODemasiadoLargo_rechaza() {
        assertThatThrownBy(() -> zonaTuristicaService.registrarPreferencia("   ", null, "travel_ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> zonaTuristicaService.registrarPreferencia("P".repeat(31), null, "travel_ana"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 caracteres");

        verify(preferenciaRepository, never()).save(any());
    }
}
