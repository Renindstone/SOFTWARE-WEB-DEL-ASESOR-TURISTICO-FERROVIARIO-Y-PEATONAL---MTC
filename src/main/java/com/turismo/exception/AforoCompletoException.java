package com.turismo.exception;

/** RF-17/RNF-08 - Caso de prueba CB-09: el cupo maximo diario ya fue alcanzado. */
public class AforoCompletoException extends RuntimeException {
    public AforoCompletoException(String message) {
        super(message);
    }
}
