package com.proyecto.supply_core.presupuesto.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Datos para definir el presupuesto de un centro de costo en un mes.
 *
 * @param centroCostoId centro de costo al que se asigna
 * @param anio          año del presupuesto
 * @param mes           mes del 1 al 12
 * @param monto         tope del mes, mayor que cero
 */
public record PresupuestoRequest(
        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser un id válido")
        Long centroCostoId,

        @NotNull(message = "es obligatorio")
        @Min(value = 2000, message = "debe ser 2000 o posterior")
        @Max(value = 2100, message = "debe ser 2100 o anterior")
        Integer anio,

        @NotNull(message = "es obligatorio")
        @Min(value = 1, message = "debe estar entre 1 y 12")
        @Max(value = 12, message = "debe estar entre 1 y 12")
        Integer mes,

        @NotNull(message = "es obligatorio")
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        @DecimalMax(value = "9999999999999999.99", message = "es demasiado grande")
        BigDecimal monto) {
}
