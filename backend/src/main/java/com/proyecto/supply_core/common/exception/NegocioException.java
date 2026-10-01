package com.proyecto.supply_core.common.exception;

/**
 * Violación de una regla de negocio o transición de estado inválida. Se responde con HTTP 409.
 * <p>Ej.: "La solicitud no está pendiente de aprobación".</p>
 */
public class NegocioException extends RuntimeException {

    private final String codigo;

    /**
     * Crea la excepción con un código estable que el frontend puede interpretar.
     *
     * @param codigo  identificador corto en mayúsculas (p. ej. {@code CORREO_DUPLICADO})
     * @param mensaje texto legible para el usuario
     */
    public NegocioException(String codigo, String mensaje) {
        super(mensaje);
        this.codigo = codigo;
    }

    /**
     * Crea la excepción con el código genérico {@code REGLA_NEGOCIO}.
     *
     * @param mensaje texto legible para el usuario
     */
    public NegocioException(String mensaje) {
        this("REGLA_NEGOCIO", mensaje);
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
