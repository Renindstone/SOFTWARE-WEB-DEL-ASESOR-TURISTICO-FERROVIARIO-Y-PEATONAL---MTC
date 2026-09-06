package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Composicion del grupo que viaja en un informe (RF-18).
 *
 * Cada fila agrupa a las personas de una misma edad, de modo que una familia
 * de dos adultos y un nino son dos filas y no tres. La categoria y los
 * subtotales quedan guardados y no se recalculan al leer: el informe es un
 * documento historico y las tarifas cambian.
 */
@Entity
@Table(name = "informe_visitante",
        uniqueConstraints = @UniqueConstraint(columnNames = {"IviIdInforme", "IviEdad"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InformeVisitante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "IviIdVisitante")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IviIdInforme", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private InformePlanificacion informe;

    @Column(name = "IviEdad", nullable = false)
    private Short edad;

    @Column(name = "IviCantidad", nullable = false)
    private Short cantidad = 1;

    /** NULL cuando el informe se emite sin servicio de tren. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IviIdCategoriaTren")
    private CategoriaVisitante categoriaTren;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "IviIdCategoriaZona", nullable = false)
    private CategoriaVisitante categoriaZona;

    @Column(name = "IviSubtotalTren", precision = 7, scale = 2, nullable = false)
    private BigDecimal subtotalTren = BigDecimal.ZERO;

    @Column(name = "IviSubtotalZona", precision = 7, scale = 2, nullable = false)
    private BigDecimal subtotalZona = BigDecimal.ZERO;

    public BigDecimal getSubtotal() {
        return subtotalTren.add(subtotalZona);
    }
}
