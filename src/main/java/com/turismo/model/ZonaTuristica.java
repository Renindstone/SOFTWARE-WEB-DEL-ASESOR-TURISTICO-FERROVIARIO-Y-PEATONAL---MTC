package com.turismo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "zona_turistica")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ZonaTuristica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ZonIdZona")
    private Integer id;

    /*
     * Las anotaciones de validacion replican las restricciones de la tabla
     * (NOT NULL, longitudes, CHECK de coordenadas y cupo, ck_zona_estado) para
     * que el formulario las anuncie con su mensaje en vez de dejar que la base
     * las rechace con un error inesperado. El navegador ya las aplica con
     * required/maxlength/min/max, pero eso no protege de un envio a mano.
     */
    @NotBlank(message = "El nombre de la zona es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres")
    @Column(name = "ZonNombre", length = 100, nullable = false)
    private String nombre;

    @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
    @Column(name = "ZonDescripcion", length = 500)
    private String descripcion;

    /**
     * Ubicacion propia del punto de interes. RutaPeatonalService la usa junto
     * con la coordenada de la estacion de origen para el calculo de Haversine
     * (seccion 5.1), y la vista del turista la usa para dibujar el destino y
     * el trazo de la ruta sobre el mapa Leaflet.
     */
    @NotNull(message = "La latitud es obligatoria")
    @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90")
    @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90")
    @Column(name = "ZonLatitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitud;

    @NotNull(message = "La longitud es obligatoria")
    @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180")
    @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180")
    @Column(name = "ZonLongitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitud;

    @NotNull(message = "Debe seleccionar la estación ferroviaria más cercana")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ZonIdEstacionCercana", nullable = false)
    private Estacion estacionCercana;

    @DecimalMin(value = "0", message = "El costo aproximado no puede ser negativo")
    @Digits(integer = 5, fraction = 2, message = "El costo aproximado admite hasta 5 enteros y 2 decimales")
    @Column(name = "ZonCostoAprox", precision = 7, scale = 2)
    private BigDecimal costoAprox;

    @Min(value = 1, message = "El cupo máximo diario debe ser mayor a cero")
    @Column(name = "ZonCupoMaximoDiario")
    private Integer cupoMaximoDiario;

    /** Valores permitidos: Activa, Inactiva (diccionario de datos 6.4). */
    @Pattern(regexp = "Activa|Inactiva", message = "El estado debe ser Activa o Inactiva")
    @Column(name = "ZonEstado", length = 10, nullable = false)
    private String estado = "Activa";

    @OneToMany(mappedBy = "zonaTuristica", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ZonaPreferencia> preferencias = new ArrayList<>();

}
