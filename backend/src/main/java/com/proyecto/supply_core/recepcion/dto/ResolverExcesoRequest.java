package com.proyecto.supply_core.recepcion.dto;

import com.proyecto.supply_core.recepcion.enums.DecisionExceso;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Decisión de compras sobre el excedente de una línea de recepción.
 *
 * @param decision ACEPTAR (entra al stock) o DEVOLVER (vuelve al proveedor)
 * @param motivo   por qué se decidió así
 */
public record ResolverExcesoRequest(
        @NotNull(message = "es obligatoria")
        DecisionExceso decision,

        @NotBlank(message = "es obligatorio")
        @Size(min = 5, max = 500, message = "entre 5 y 500 caracteres")
        String motivo) {
}
