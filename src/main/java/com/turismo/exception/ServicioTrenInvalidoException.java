package com.turismo.exception;

/**
 * RF-13: el servicio de tren no supera las reglas que relacionan varios de
 * sus campos entre si y que, por tanto, Bean Validation no puede expresar
 * campo a campo: que el origen y el destino sean estaciones distintas y que
 * el horario de llegada se corresponda con la salida mas el tiempo de
 * transito declarado.
 *
 * Es distinta de TarifaInvalidaException, que cubre un unico campo y nace
 * del feed de PeruRail (CB-06).
 */
public class ServicioTrenInvalidoException extends RuntimeException {
    public ServicioTrenInvalidoException(String message) {
        super(message);
    }
}
