package com.turismo.model;

import jakarta.persistence.*;
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

    @Column(name = "ZonNombre", length = 100, nullable = false)
    private String nombre;

    @Column(name = "ZonDescripcion", length = 500)
    private String descripcion;

    /**
     * Ubicacion propia del punto de interes. RutaPeatonalService la usa junto
     * con la coordenada de la estacion de origen para el calculo de Haversine
     * (seccion 5.1), y la vista del turista la usa para dibujar el destino y
     * el trazo de la ruta sobre el mapa Leaflet.
     */
    @Column(name = "ZonLatitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal latitud;

    @Column(name = "ZonLongitud", precision = 9, scale = 6, nullable = false)
    private BigDecimal longitud;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ZonIdEstacionCercana", nullable = false)
    private Estacion estacionCercana;

    @Column(name = "ZonCostoAprox", precision = 7, scale = 2)
    private BigDecimal costoAprox;

    @Column(name = "ZonCupoMaximoDiario")
    private Integer cupoMaximoDiario;

    /** Valores permitidos: Activa, Inactiva (diccionario de datos 6.4). */
    @Column(name = "ZonEstado", length = 10, nullable = false)
    private String estado = "Activa";

    @OneToMany(mappedBy = "zonaTuristica", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ZonaPreferencia> preferencias = new ArrayList<>();

}
