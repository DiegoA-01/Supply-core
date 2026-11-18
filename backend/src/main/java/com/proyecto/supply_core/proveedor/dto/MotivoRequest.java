package com.proyecto.supply_core.proveedor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Motivo obligatorio al rechazar o suspender un proveedor (queda en el proveedor y en la auditoría).
 *
 * @param motivo explicación para el proveedor y el auditor
 */
public record MotivoRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "debe tener entre 5 y 500 caracteres")
        String motivo) {
}
