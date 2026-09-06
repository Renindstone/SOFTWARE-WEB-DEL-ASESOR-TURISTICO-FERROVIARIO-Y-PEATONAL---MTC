package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "ruta_peatonal")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RutaPeatonal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "RutIdRuta")
    private Integer id;

    @Column(name = "RutNombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "RutDescripcion", length = 500)
    private String descripcion;

    @Column(name = "RutDistanciaKm", precision = 5, scale = 2, nullable = false)
    private BigDecimal distanciaKm;

    @Column(name = "RutTiempoEstimadoMin", nullable = false)
    private Integer tiempoEstimadoMin;

    /** Nivel de dificultad, ahora tabla parametrica propia (RNF-06). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RutIdDificultad", nullable = false)
    private Dificultad dificultad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RutIdEstacionOrigen", nullable = false)
    private Estacion estacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RutIdZonaDestino", nullable = false)
    private ZonaTuristica zonaDestino;

    @Column(name = "RutEsIdaVuelta", nullable = false)
    private Boolean esIdaVuelta = Boolean.TRUE;

}
