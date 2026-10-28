package com.proyecto.supply_core.presupuesto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Corrección del monto de un presupuesto. El centro de costo y el mes no cambian.
 *
 * @param monto nuevo tope del mes, mayor que cero
 */
public record PresupuestoUpdateRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        @DecimalMax(value = "9999999999999999.99", message = "es demasiado grande")
        BigDecimal monto) {
}
