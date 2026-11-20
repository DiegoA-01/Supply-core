package com.proyecto.supply_core.cartera.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.caja.enums.MedioPago;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Abono de un cliente en el turno de la sesión.
 *
 * @param clienteId  cliente
 * @param valor      valor (> 0, 2 decimales)
 * @param medioPago  medio con que paga
 * @param referencia autorización o número de transferencia (opcional)
 */
public record AbonoRequest(
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser un id válido") Long clienteId,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "admite hasta 2 decimales") BigDecimal valor,
        @NotNull(message = "es obligatorio") MedioPago medioPago,
        @Size(max = 40, message = "admite hasta 40 caracteres") String referencia) {
}
