package com.proyecto.supply_core.ia.client;

/** Falla de la IA (sin respuesta, rechazo, formato inválido). El procesamiento queda en ERROR con este mensaje. */
public class IAException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param mensaje descripción legible del problema
     */
    public IAException(String mensaje) {
        super(mensaje);
    }

    /**
     * @param mensaje descripción legible del problema
     * @param causa   excepción original
     */
    public IAException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
