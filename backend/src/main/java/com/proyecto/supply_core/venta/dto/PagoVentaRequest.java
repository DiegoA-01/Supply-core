package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Un pago. En efectivo, {@code monto} es lo que entrega el cliente (el sistema calcula el cambio). De una tarjeta
 * solo se aceptan los últimos 4 dígitos: nunca el número completo ni el CVV.
 *
 * @param medio      medio de pago
 * @param monto      valor (> 0, 2 decimales)
 * @param referencia número de autorización del datáfono o de la transferencia
 * @param ultimos4   últimos 4 dígitos de la tarjeta
 */
public record PagoVentaRequest(
        @NotNull(message = "es obligatorio") MedioPagoVenta medio,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "admite hasta 2 decimales") BigDecimal monto,
        @Size(max = 40, message = "admite hasta 40 caracteres") String referencia,
        @Pattern(regexp = "\\d{4}", message = "deben ser exactamente los últimos 4 dígitos") String ultimos4) {
}
