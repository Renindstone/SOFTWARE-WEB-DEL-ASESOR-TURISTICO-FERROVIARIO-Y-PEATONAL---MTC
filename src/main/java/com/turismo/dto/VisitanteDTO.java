package com.turismo.dto;

import java.math.BigDecimal;

/**
 * Una linea de la composicion del grupo (RF-18): tantas personas de una misma
 * edad, con la categoria tarifaria que les corresponde en cada ambito.
 *
 * Al entrar solo lleva edad y cantidad, que es lo que declara el turista; los
 * demas campos los completa InformeService al aplicar las tarifas.
 */
public class VisitanteDTO {

    private Integer edad;
    private Integer cantidad = 1;
    private String categoriaTren;
    private String categoriaZona;
    private BigDecimal subtotalTren;
    private BigDecimal subtotalZona;

    public VisitanteDTO() {
    }

    public VisitanteDTO(Integer edad, Integer cantidad) {
        this.edad = edad;
        this.cantidad = cantidad;
    }

    public Integer getEdad() {
        return edad;
    }

    public void setEdad(Integer edad) {
        this.edad = edad;
    }

    public Integer getCantidad() {
        return cantidad;
    }

    public void setCantidad(Integer cantidad) {
        this.cantidad = cantidad;
    }

    public String getCategoriaTren() {
        return categoriaTren;
    }

    public void setCategoriaTren(String categoriaTren) {
        this.categoriaTren = categoriaTren;
    }

    public String getCategoriaZona() {
        return categoriaZona;
    }

    public void setCategoriaZona(String categoriaZona) {
        this.categoriaZona = categoriaZona;
    }

    public BigDecimal getSubtotalTren() {
        return subtotalTren;
    }

    public void setSubtotalTren(BigDecimal subtotalTren) {
        this.subtotalTren = subtotalTren;
    }

    public BigDecimal getSubtotalZona() {
        return subtotalZona;
    }

    public void setSubtotalZona(BigDecimal subtotalZona) {
        this.subtotalZona = subtotalZona;
    }

    public BigDecimal getSubtotal() {
        BigDecimal tren = subtotalTren == null ? BigDecimal.ZERO : subtotalTren;
        BigDecimal zona = subtotalZona == null ? BigDecimal.ZERO : subtotalZona;
        return tren.add(zona);
    }
}
