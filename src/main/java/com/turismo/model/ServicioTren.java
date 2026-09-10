package com.turismo.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    /** @Positive replica en la aplicacion el CHECK ck_servicio_tiempo de la base. */
    @NotNull(message = "El tiempo de tránsito es obligatorio")
    @Positive(message = "El tiempo de tránsito debe ser mayor a cero minutos")
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
