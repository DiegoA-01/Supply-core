package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Línea que manda la pantalla: solo qué, cuánto y qué descuento pide. El precio y los montos los pone Java.
 *
 * @param productoId     producto
 * @param unidadMedidaId unidad en que se vende ({@code null} = la del producto)
 * @param cantidad       cantidad (> 0, hasta 4 decimales)
 * @param descuentoPct   descuento pedido en porcentaje ({@code null} = 0)
 */
public record LineaVentaRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long productoId,
        @Positive(message = "debe ser un id válido") Long unidadMedidaId,
        @NotNull(message = "es obligatoria") @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 14, fraction = 4, message = "admite hasta 4 decimales") BigDecimal cantidad,
        @DecimalMin(value = "0", message = "no puede ser negativo") @DecimalMax(value = "100", message = "no puede pasar de 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 2 decimales") BigDecimal descuentoPct) {
}
