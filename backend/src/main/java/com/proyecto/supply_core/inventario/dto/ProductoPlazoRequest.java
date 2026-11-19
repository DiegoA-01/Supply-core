package com.proyecto.supply_core.inventario.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Plazo de reabastecimiento propio de un producto. Un valor vacío ({@code null}) vuelve al valor por defecto de la
 * empresa.
 *
 * @param plazoEntregaDias días hábiles que tarda el proveedor en entregarlo, de 0 a 120 ({@code null} = el de la empresa)
 * @param diasSeguridad    días hábiles de colchón, de 0 a 60 ({@code null} = el de la empresa)
 */
public record ProductoPlazoRequest(
        @Min(value = 0, message = "no puede ser negativo")
        @Max(value = 120, message = "no puede superar 120 días hábiles")
        Integer plazoEntregaDias,
        @Min(value = 0, message = "no puede ser negativo")
        @Max(value = 60, message = "no puede superar 60 días hábiles")
        Integer diasSeguridad) {
}
