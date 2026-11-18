package com.proyecto.supply_core.recepcion.dto;

import jakarta.validation.constraints.Size;

/**
 * Cierre de la recepción de una orden.
 *
 * @param motivo obligatorio si se cierra con faltante (orden RECIBIDA_PARCIAL); opcional si llegó todo
 */
public record CerrarRecepcionRequest(
        @Size(max = 500, message = "máximo 500 caracteres")
        String motivo) {
}
