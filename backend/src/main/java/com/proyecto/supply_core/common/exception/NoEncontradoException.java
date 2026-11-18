package com.proyecto.supply_core.common.exception;

/** El recurso pedido no existe. Se responde con HTTP 404. */
public class NoEncontradoException extends RuntimeException {

    /**
     * Crea la excepción con un mensaje libre.
     *
     * @param mensaje texto legible para el usuario
     */
    public NoEncontradoException(String mensaje) {
        super(mensaje);
    }

    /**
     * Crea la excepción con el mensaje estándar "{recurso} con id {id} no existe".
     *
     * @param recurso nombre del recurso (p. ej. "Usuario")
     * @param id      identificador buscado
     */
    public NoEncontradoException(String recurso, Object id) {
        super(recurso + " con id " + id + " no existe");
    }
}
