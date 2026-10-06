package com.proyecto.supply_core.compra.analisis.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Nueva versión de pesos: un peso (0–100) por código de criterio; deben estar todos y sumar 100.
 *
 * @param pesos p. ej. {"PRECIO": 35, "CALIDAD": 20, ...}
 */
public record PesosRequest(
        @NotNull(message = "es obligatorio")
        @NotEmpty(message = "debe incluir los pesos de los criterios")
        Map<String, @NotNull(message = "no puede estar vacío")
                @DecimalMin(value = "0", message = "no puede ser negativo")
                @DecimalMax(value = "100", message = "no puede ser mayor que 100") BigDecimal> pesos) {
}
