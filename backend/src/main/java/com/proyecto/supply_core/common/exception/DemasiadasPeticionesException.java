package com.proyecto.supply_core.common.exception;

/**
 * El cliente superó el límite de peticiones permitido. Se responde con HTTP 429.
 */
public class DemasiadasPeticionesException extends RuntimeException {

    /**
     * Crea la excepción.
     *
     * @param mensaje texto legible para el usuario
     */
    public DemasiadasPeticionesException(String mensaje) {
        super(mensaje);
    }
}
