package com.turismo.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "preferencia")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Preferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "PreIdPreferencia")
    private Integer id;

    @Column(name = "PreNombre", length = 30, nullable = false, unique = true)
    private String nombre;

    @Column(name = "PreDescripcion", length = 150)
    private String descripcion;

}
