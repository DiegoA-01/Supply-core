package com.proyecto.supply_core.contrato.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo para anular un contrato.
 *
 * @param motivo por qué se anula
 */
public record MotivoContratoRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo) {
}
