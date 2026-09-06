package com.turismo.dto;

import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * Datos ingresados por el turista en el formulario de preferencias
 * (RF-01) y en la seleccion de estacion de partida (RF-02).
 *
 * Los cuatro campos son opcionales y funcionan como filtros que se van
 * sumando: sin ninguno, el buscador muestra el catalogo completo de zonas
 * activas; cada filtro que el turista anade recorta ese listado. Por eso
 * aqui solo queda la validacion de rango del tiempo, que sigue teniendo
 * sentido cuando el campo viene informado.
 */
public class BusquedaZonaDTO {

    /** Ids de Preferencia seleccionados de la tabla parametrica (RNF-06). */
    private List<Integer> idsPreferencia;

    @Min(value = 1, message = "El tiempo disponible debe ser mayor a cero")
    private Integer tiempoDisponibleMin;

    /** Nombre de un nivel de la tabla dificultad; actua como techo. */
    private String dificultad;

    private Integer idEstacionOrigen;

    /** Numero de filtros activos, para que la vista pueda anunciarlos. */
    public int contarFiltrosActivos() {
        int filtros = 0;
        if (idEstacionOrigen != null) {
            filtros++;
        }
        if (idsPreferencia != null && !idsPreferencia.isEmpty()) {
            filtros++;
        }
        if (tiempoDisponibleMin != null) {
            filtros++;
        }
        if (dificultad != null && !dificultad.isBlank()) {
            filtros++;
        }
        return filtros;
    }

    public boolean isSinFiltros() {
        return contarFiltrosActivos() == 0;
    }

    public List<Integer> getIdsPreferencia() {
        return idsPreferencia;
    }

    public void setIdsPreferencia(List<Integer> idsPreferencia) {
        this.idsPreferencia = idsPreferencia;
    }

    public Integer getTiempoDisponibleMin() {
        return tiempoDisponibleMin;
    }

    public void setTiempoDisponibleMin(Integer tiempoDisponibleMin) {
        this.tiempoDisponibleMin = tiempoDisponibleMin;
    }

    public String getDificultad() {
        return dificultad;
    }

    public void setDificultad(String dificultad) {
        this.dificultad = dificultad;
    }

    public Integer getIdEstacionOrigen() {
        return idEstacionOrigen;
    }

    public void setIdEstacionOrigen(Integer idEstacionOrigen) {
        this.idEstacionOrigen = idEstacionOrigen;
    }
}
