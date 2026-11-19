package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Mínimo y máximo de un producto en una sede.
 *
 * @param stockMinimo por debajo de esto se avisa en la sede (0 = sin mínimo propio)
 * @param stockMaximo tope en la sede, opcional (mayor o igual al mínimo)
 */
public record ProductoSedeRequest(
        @NotNull(message = "es obligatorio")
        @PositiveOrZero(message = "no puede ser negativo")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMinimo,
        @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "máximo 14 enteros y 4 decimales")
        BigDecimal stockMaximo) {

    /**
     * @return {@code true} si no hay máximo o es mayor o igual al mínimo
     */
    @AssertTrue(message = "stockMaximo no puede ser menor que stockMinimo")
    public boolean isRangoValido() {
        return stockMaximo == null || stockMinimo == null || stockMaximo.compareTo(stockMinimo) >= 0;
    }
}
