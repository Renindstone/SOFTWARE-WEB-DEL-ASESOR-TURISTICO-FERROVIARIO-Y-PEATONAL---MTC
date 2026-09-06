package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad intermedia que resuelve la relacion N:M entre ZonaTuristica y
 * Preferencia (una zona puede pertenecer simultaneamente a mas de una
 * categoria turistica). Ver seccion 6.1 y 6.4 de ProyectoFinalSOftware.docx.
 */
@Entity
@Table(name = "zona_preferencia",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ZprIdZonaTuristica", "ZprIdPreferencia"}))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ZonaPreferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ZprIdZonaPreferencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ZprIdZonaTuristica", nullable = false)
    private ZonaTuristica zonaTuristica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ZprIdPreferencia", nullable = false)
    private Preferencia preferencia;

}
