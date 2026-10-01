package com.proyecto.supply_core.common.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cuerpo único de error de toda la API.
 *
 * @param codigo    código estable (p. ej. {@code VALIDACION}, {@code NO_ENCONTRADO})
 * @param mensaje   resumen legible
 * @param detalles  lista "campo: mensaje" en errores de validación; vacía en los demás
 * @param timestamp momento en que se produjo el error
 */
public record ErrorResponse(
        String codigo,
        String mensaje,
        List<String> detalles,
        LocalDateTime timestamp) {

    /**
     * Crea un error sin detalles.
     *
     * @param codigo  código estable
     * @param mensaje resumen legible
     * @return respuesta con la hora actual
     */
    public static ErrorResponse of(String codigo, String mensaje) {
        return new ErrorResponse(codigo, mensaje, List.of(), LocalDateTime.now());
    }

    /**
     * Crea un error con detalles (típicamente errores de validación por campo).
     *
     * @param codigo   código estable
     * @param mensaje  resumen legible
     * @param detalles mensajes individuales
     * @return respuesta con la hora actual
     */
    public static ErrorResponse of(String codigo, String mensaje, List<String> detalles) {
        return new ErrorResponse(codigo, mensaje, List.copyOf(detalles), LocalDateTime.now());
    }
}
