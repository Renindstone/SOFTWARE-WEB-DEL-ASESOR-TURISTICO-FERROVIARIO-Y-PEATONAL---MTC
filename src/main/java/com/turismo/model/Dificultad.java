package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Nivel de dificultad de una ruta peatonal (RF-05).
 *
 * RNF-06 pide poder "modificar los parametros de dificultad sin alterar el
 * codigo fuente principal, mediante tablas parametricas": por eso los
 * umbrales de distancia y el ritmo de caminata viven aqui y no como
 * constantes de RutaPeatonalService.
 */
@Entity
@Table(name = "dificultad")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Dificultad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "DifIdDificultad")
    private Integer id;

    /** Valores sembrados: Baja, Media, Alta. */
    @Column(name = "DifNombre", length = 10, nullable = false, unique = true)
    private String nombre;

    @Column(name = "DifDescripcion", length = 150)
    private String descripcion;

    /** Permite comparar niveles sin depender del texto del nombre. */
    @Column(name = "DifOrden", nullable = false, unique = true)
    private Short orden;

    /**
     * Tope de kilometros del circuito completo (ida y vuelta) que todavia se
     * clasifica en este nivel. NULL en el nivel mas alto, que no tiene tope.
     */
    @Column(name = "DifDistanciaMaximaKm", precision = 5, scale = 2)
    private BigDecimal distanciaMaximaKm;

    /** Minutos por kilometro: un ascenso exigente se camina mas lento. */
    @Column(name = "DifVelocidadMinPorKm", nullable = false)
    private Short velocidadMinPorKm;
}
