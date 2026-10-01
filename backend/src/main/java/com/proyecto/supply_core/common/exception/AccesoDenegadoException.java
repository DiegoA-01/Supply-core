package com.proyecto.supply_core.common.exception;

/** El usuario está autenticado pero no tiene el rol o el acceso al recurso. Se responde con HTTP 403. */
public class AccesoDenegadoException extends RuntimeException {

    /**
     * Crea la excepción.
     *
     * @param mensaje texto legible para el usuario
     */
    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
