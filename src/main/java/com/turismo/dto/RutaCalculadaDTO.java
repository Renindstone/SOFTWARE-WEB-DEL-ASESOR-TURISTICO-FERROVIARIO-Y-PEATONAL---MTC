package com.turismo.dto;

import java.math.BigDecimal;

/**
 * Resultado del motor de rutas (RF-04/RF-05): circuito peatonal de ida y
 * vuelta calculado con la formula de Haversine (ver 5.1 del documento).
 */
public class RutaCalculadaDTO {

    private String nombre;
    private BigDecimal distanciaKm;
    private Integer tiempoEstimadoMin;
    /** Nombre del nivel: Baja, Media, Alta (tabla dificultad). */
    private String dificultad;
    /** DifOrden del nivel; permite compararlo sin depender de su nombre. */
    private Short ordenDificultad;
    private Boolean esIdaVuelta = Boolean.TRUE;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public BigDecimal getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(BigDecimal distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public Integer getTiempoEstimadoMin() {
        return tiempoEstimadoMin;
    }

    public void setTiempoEstimadoMin(Integer tiempoEstimadoMin) {
        this.tiempoEstimadoMin = tiempoEstimadoMin;
    }

    public String getDificultad() {
        return dificultad;
    }

    public void setDificultad(String dificultad) {
        this.dificultad = dificultad;
    }

    public Short getOrdenDificultad() {
        return ordenDificultad;
    }

    public void setOrdenDificultad(Short ordenDificultad) {
        this.ordenDificultad = ordenDificultad;
    }

    public Boolean getEsIdaVuelta() {
        return esIdaVuelta;
    }

    public void setEsIdaVuelta(Boolean esIdaVuelta) {
        this.esIdaVuelta = esIdaVuelta;
    }
}
