package com.proyecto.supply_core.factura.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo para aprobar una factura que no cuadra o para anular una registrada por error.
 *
 * @param motivo por qué se decide así (p. ej. "El proveedor cobró el flete pactado por correo")
 */
public record MotivoFacturaRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo) {
}
