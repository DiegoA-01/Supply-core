package com.proyecto.supply_core.venta.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Anulación de una venta del turno abierto.
 *
 * @param motivo       por qué se anula (error de digitación, el cliente desistió...)
 * @param autorizacion permiso de un solo uso del supervisor (acción ANULAR_VENTA)
 */
public record AnularVentaRequest(
        @NotBlank(message = "es obligatorio") @Size(min = 5, max = 300, message = "debe tener entre 5 y 300 caracteres") String motivo,
        @NotBlank(message = "es obligatoria") @Size(max = 100, message = "no es una autorización válida") String autorizacion) {
}
