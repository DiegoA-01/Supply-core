package com.proyecto.supply_core.common.exception;

/**
 * Un parámetro de la petición no es aceptable (p. ej. ordenar por un campo no permitido).
 * Se responde con HTTP 400.
 */
public class ParametroInvalidoException extends RuntimeException {

    /**
     * Crea la excepción.
     *
     * @param mensaje texto legible para el usuario
     */
    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
