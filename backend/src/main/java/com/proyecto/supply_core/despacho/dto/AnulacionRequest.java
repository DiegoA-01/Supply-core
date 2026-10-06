package com.proyecto.supply_core.despacho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo para anular un despacho.
 *
 * @param motivo por qué se anula (p. ej. "El cliente devolvió la mercancía")
 */
public record AnulacionRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo) {
}
