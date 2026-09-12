package com.turismo.exception;

/**
 * RF-15 - Caso de prueba CB-04: el feed de SENAMHI trae un valor fuera de
 * rango (p. ej. CliProbabilidadLluvia fuera de 0-100) y se descarta.
 */
public class FeedInvalidoException extends RuntimeException {
    public FeedInvalidoException(String message) {
        super(message);
    }
}
