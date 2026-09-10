package com.turismo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "servicio_tren")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ServicioTren {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SerIdServicio")
    private Integer id;

    @NotNull(message = "El horario de salida es obligatorio")
    @Column(name = "SerHorarioSalida", nullable = false)
    private LocalTime horarioSalida;

    @NotNull(message = "El horario de llegada es obligatorio")
    @Column(name = "SerHorarioLlegada", nullable = false)
    private LocalTime horarioLlegada;

    /**
     * Deliberadamente sin @NotNull ni @Positive, a diferencia del resto de los
     * campos obligatorios.
     *
     * No es un dato que el usuario aporte: sale de restar los dos horarios, y
     * ServicioTrenService.completarTiempoTransito lo deriva cuando llega
     * ausente o en cero. Con las anotaciones puestas, el enlazado lo rechazaba
     * antes de llegar al servicio, de modo que un fallo del JavaScript del
     * formulario dejaba al administrador ante un campo obligatorio que ademas
     * es de solo lectura: no podia rellenarlo ni seguir.
     *
     * El valor no queda sin comprobar. Los dos horarios si son @NotNull, asi
     * que cuando la peticion llega al servicio siempre hay de donde derivarlo,
     * y validarHorarios rechaza despues cualquier valor que no concuerde con
     * ellos. El CHECK ck_servicio_tiempo de la base cierra el caso extremo.
     */
    @Column(name = "SerTiempoTransitoMin", nullable = false)
    private Integer tiempoTransitoMin;

    /**
     * El CHECK ck_servicio_tarifa de la base admite 0, pero la regla de negocio
     * (CB-06, PeruRailClient.validarTarifaPeruRail) exige un valor mayor a cero:
     * la aplicacion es deliberadamente mas estricta que el motor.
     */
    @NotNull(message = "La tarifa es obligatoria")
    @DecimalMin(value = "0.01", message = "La tarifa debe ser un valor mayor a cero")
    @Column(name = "SerTarifa", precision = 7, scale = 2, nullable = false)
    private BigDecimal tarifa;

    @NotNull(message = "La estación de origen es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SerIdEstacionOrigen", nullable = false)
    private Estacion estacionOrigen;

    @NotNull(message = "La estación de destino es obligatoria")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SerIdEstacionDestino", nullable = false)
    private Estacion estacionDestino;

}
