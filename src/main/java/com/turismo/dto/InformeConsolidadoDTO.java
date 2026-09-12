package com.turismo.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Informe consolidado (RF-08): ruta, clima, tiempo estimado, dificultad
 * y tarifa del tren, exportable en PDF/HTML (Modulo de Informes).
 */
public class InformeConsolidadoDTO {

    private String codigo;
    private LocalDate fechaVisita;
    private String estacionOrigen;
    private String zonaDestino;
    private RutaCalculadaDTO ruta;
    private BigDecimal temperaturaMinimaC;
    private BigDecimal temperaturaMaximaC;
    private BigDecimal probabilidadLluvia;
    private String estadoClima;
    private BigDecimal tarifaTren;
    /** Tramo y hora de salida del tren cuya tarifa se tomo, para que el informe diga cual es. */
    private String servicioTren;
    private BigDecimal costoZona;
    private BigDecimal totalEstimado;
    /** Cupo restante de la zona para la fecha; null si la zona no controla aforo (RF-17). */
    private Integer cupoDisponible;
    /** RF-19: composicion del grupo, una linea por edad. */
    private List<VisitanteDTO> visitantes = new ArrayList<>();

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public LocalDate getFechaVisita() {
        return fechaVisita;
    }

    public void setFechaVisita(LocalDate fechaVisita) {
        this.fechaVisita = fechaVisita;
    }

    public String getEstacionOrigen() {
        return estacionOrigen;
    }

    public void setEstacionOrigen(String estacionOrigen) {
        this.estacionOrigen = estacionOrigen;
    }

    public String getZonaDestino() {
        return zonaDestino;
    }

    public void setZonaDestino(String zonaDestino) {
        this.zonaDestino = zonaDestino;
    }

    public RutaCalculadaDTO getRuta() {
        return ruta;
    }

    public void setRuta(RutaCalculadaDTO ruta) {
        this.ruta = ruta;
    }

    public BigDecimal getTemperaturaMinimaC() {
        return temperaturaMinimaC;
    }

    public void setTemperaturaMinimaC(BigDecimal temperaturaMinimaC) {
        this.temperaturaMinimaC = temperaturaMinimaC;
    }

    public BigDecimal getTemperaturaMaximaC() {
        return temperaturaMaximaC;
    }

    public void setTemperaturaMaximaC(BigDecimal temperaturaMaximaC) {
        this.temperaturaMaximaC = temperaturaMaximaC;
    }

    public BigDecimal getProbabilidadLluvia() {
        return probabilidadLluvia;
    }

    public void setProbabilidadLluvia(BigDecimal probabilidadLluvia) {
        this.probabilidadLluvia = probabilidadLluvia;
    }

    public String getEstadoClima() {
        return estadoClima;
    }

    public void setEstadoClima(String estadoClima) {
        this.estadoClima = estadoClima;
    }

    public BigDecimal getTarifaTren() {
        return tarifaTren;
    }

    public void setTarifaTren(BigDecimal tarifaTren) {
        this.tarifaTren = tarifaTren;
    }

    public String getServicioTren() {
        return servicioTren;
    }

    public void setServicioTren(String servicioTren) {
        this.servicioTren = servicioTren;
    }

    public BigDecimal getCostoZona() {
        return costoZona;
    }

    public void setCostoZona(BigDecimal costoZona) {
        this.costoZona = costoZona;
    }

    public Integer getCupoDisponible() {
        return cupoDisponible;
    }

    public void setCupoDisponible(Integer cupoDisponible) {
        this.cupoDisponible = cupoDisponible;
    }

    public List<VisitanteDTO> getVisitantes() {
        return visitantes;
    }

    public void setVisitantes(List<VisitanteDTO> visitantes) {
        this.visitantes = visitantes;
    }

    /** Numero de personas del grupo: la suma del detalle, no un dato aparte. */
    public int getTotalPersonas() {
        return visitantes.stream()
                .mapToInt(visitante -> visitante.getCantidad() == null ? 0 : visitante.getCantidad())
                .sum();
    }

    public BigDecimal getTotalEstimado() {
        return totalEstimado;
    }

    public void setTotalEstimado(BigDecimal totalEstimado) {
        this.totalEstimado = totalEstimado;
    }
}
