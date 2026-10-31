package com.proyecto.supply_core.common.exception;

/**
 * Se pidió exportar en un formato que no existe o que ese dato no ofrece (400 {@code FORMATO_NO_SOPORTADO}).
 */
public class FormatoNoSoportadoException extends RuntimeException {

    /**
     * @param mensaje explicación legible con los formatos válidos
     */
    public FormatoNoSoportadoException(String mensaje) {
        super(mensaje);
    }
}
