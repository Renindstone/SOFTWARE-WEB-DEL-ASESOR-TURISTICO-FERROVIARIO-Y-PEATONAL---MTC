package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Categoria tarifaria segun la edad del visitante.
 *
 * Los cortes de edad NO son los mismos en los dos proveedores: en el tren un
 * pasajero de 15 anos ya paga tarifa de adulto, mientras que en la zona sigue
 * pagando tarifa de nino hasta los 17. De ahi el ambito.
 */
@Entity
@Table(name = "categoria_visitante",
        uniqueConstraints = @UniqueConstraint(columnNames = {"CatAmbito", "CatNombre"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CategoriaVisitante {

    /** Ambito de aplicacion de la tarifa. */
    public static final String AMBITO_TREN = "Tren";
    public static final String AMBITO_ZONA = "Zona";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CatIdCategoria")
    private Integer id;

    /** Valores permitidos: Tren, Zona. */
    @Column(name = "CatAmbito", length = 10, nullable = false)
    private String ambito;

    /** Valores sembrados: Infante, Nino, Adulto. */
    @Column(name = "CatNombre", length = 30, nullable = false)
    private String nombre;

    @Column(name = "CatEdadMinima", nullable = false)
    private Short edadMinima;

    /** NULL indica que la categoria no tiene tope de edad. */
    @Column(name = "CatEdadMaxima")
    private Short edadMaxima;

    /** Se aplica sobre el precio base (SerTarifa o ZonCostoAprox). */
    @Column(name = "CatFactorPrecio", precision = 5, scale = 4, nullable = false)
    private BigDecimal factorPrecio;

    @Column(name = "CatDescripcion", length = 150)
    private String descripcion;

    /** La edad cae dentro del tramo de esta categoria. */
    public boolean cubre(int edad) {
        return edad >= edadMinima && (edadMaxima == null || edad <= edadMaxima);
    }
}
