package com.proyecto.supply_core.common.exception;

/**
 * Falta el token, es inválido o expiró, o las credenciales no son válidas.
 * Se responde con HTTP 401.
 */
public class NoAutenticadoException extends RuntimeException {

    /**
     * Crea la excepción.
     *
     * @param mensaje texto legible; nunca debe revelar qué dato de login falló
     */
    public NoAutenticadoException(String mensaje) {
        super(mensaje);
    }
}
