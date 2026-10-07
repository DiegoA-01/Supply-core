package com.proyecto.supply_core.pago.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo para anular un pago registrado por error.
 *
 * @param motivo por qué se anula (p. ej. "Se digitó el valor de otra orden")
 */
public record AnularPagoRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo) {
}
