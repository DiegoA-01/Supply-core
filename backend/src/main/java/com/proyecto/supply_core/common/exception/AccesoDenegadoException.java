package com.proyecto.supply_core.common.exception;

/**
 * El usuario está autenticado pero no tiene el rol, el módulo o el acceso al recurso. Se responde con HTTP 403.
 * Puede llevar un código estable ({@code MODULO_NO_CONTRATADO}, {@code SUSCRIPCION_SUSPENDIDA}...) para que el
 * frontend decida qué mostrar; sin código, el genérico {@code ACCESO_DENEGADO}.
 */
public class AccesoDenegadoException extends RuntimeException {

    /** Código usado cuando no se indica otro. */
    public static final String CODIGO_GENERICO = "ACCESO_DENEGADO";

    private final String codigo;

    /**
     * Crea la excepción con el código genérico.
     *
     * @param mensaje texto legible para el usuario
     */
    public AccesoDenegadoException(String mensaje) {
        this(CODIGO_GENERICO, mensaje);
    }

    /**
     * Crea la excepción con un código estable.
     *
     * @param codigo  identificador corto en mayúsculas (p. ej. {@code MODULO_NO_CONTRATADO})
     * @param mensaje texto legible para el usuario
     */
    public AccesoDenegadoException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    /**
     * Devuelve el código estable del error.
     *
     * @return código en mayúsculas, nunca {@code null}
     */
    public String getCodigo() {
        return codigo;
    }
}
