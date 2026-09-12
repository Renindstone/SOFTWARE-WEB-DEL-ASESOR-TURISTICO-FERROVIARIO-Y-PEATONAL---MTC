package com.turismo.exception;

/**
 * RF-14 - Caso de prueba CB-06: la tarifa recibida de PeruRail es negativa
 * o igual a cero y se rechaza antes de persistirla en ServicioTren.
 */
public class TarifaInvalidaException extends RuntimeException {
    public TarifaInvalidaException(String message) {
        super(message);
    }
}
