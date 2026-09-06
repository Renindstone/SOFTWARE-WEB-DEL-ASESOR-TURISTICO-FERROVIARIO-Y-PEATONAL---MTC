package com.turismo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Zona turistica filtrada segun las preferencias del turista (RF-03),
 * ya asociada a su(s) tipo(s) de turismo (relacion N:M ZonaTipoTurismo).
 */
public class ZonaResultadoDTO {

    private Integer idZona;
    private String nombre;
    private String descripcion;
    private List<String> tiposTurismo;
    private BigDecimal costoAproximado;
    private Integer cupoMaximoDiario;
    /**
     * Estacion desde la que se midio el circuito. Coincide con la que eligio
     * el turista cuando declaro una; si no filtro por estacion, es la
     * estacion cercana de la propia zona (ZonIdEstacionCercana).
     */
    private Integer idEstacionCercana;
    private String nombreEstacionCercana;
    private BigDecimal latitud;
    private BigDecimal longitud;
    /** Circuito de ida y vuelta ya calculado para esta zona (RF-04/RF-05). */
    private RutaCalculadaDTO ruta;

    public Integer getIdZona() {
        return idZona;
    }

    public void setIdZona(Integer idZona) {
        this.idZona = idZona;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public List<String> getTiposTurismo() {
        return tiposTurismo;
    }

    public void setTiposTurismo(List<String> tiposTurismo) {
        this.tiposTurismo = tiposTurismo;
    }

    public BigDecimal getCostoAproximado() {
        return costoAproximado;
    }

    public void setCostoAproximado(BigDecimal costoAproximado) {
        this.costoAproximado = costoAproximado;
    }

    public Integer getCupoMaximoDiario() {
        return cupoMaximoDiario;
    }

    public void setCupoMaximoDiario(Integer cupoMaximoDiario) {
        this.cupoMaximoDiario = cupoMaximoDiario;
    }

    public Integer getIdEstacionCercana() {
        return idEstacionCercana;
    }

    public void setIdEstacionCercana(Integer idEstacionCercana) {
        this.idEstacionCercana = idEstacionCercana;
    }

    public String getNombreEstacionCercana() {
        return nombreEstacionCercana;
    }

    public void setNombreEstacionCercana(String nombreEstacionCercana) {
        this.nombreEstacionCercana = nombreEstacionCercana;
    }

    public BigDecimal getLatitud() {
        return latitud;
    }

    public void setLatitud(BigDecimal latitud) {
        this.latitud = latitud;
    }

    public BigDecimal getLongitud() {
        return longitud;
    }

    public void setLongitud(BigDecimal longitud) {
        this.longitud = longitud;
    }

    public RutaCalculadaDTO getRuta() {
        return ruta;
    }

    public void setRuta(RutaCalculadaDTO ruta) {
        this.ruta = ruta;
    }
}
