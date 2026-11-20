package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Descuento máximo sin autorización de un rol.
 *
 * @param rol                código del rol (CAJERO, SUPERVISOR_VENTAS o VENDEDOR)
 * @param descuentoMaximoPct porcentaje máximo (0 a 100)
 */
public record LimiteDescuento(
        @NotBlank(message = "es obligatorio") String rol,
        @NotNull(message = "es obligatorio") @DecimalMin(value = "0", message = "no puede ser negativo")
        @DecimalMax(value = "100", message = "no puede pasar de 100")
        @Digits(integer = 3, fraction = 2, message = "admite hasta 2 decimales") BigDecimal descuentoMaximoPct) {
}
