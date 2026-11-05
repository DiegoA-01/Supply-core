package com.proyecto.supply_core.pago.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.pago.enums.MetodoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Datos de un pago hecho a una orden. El comprobante se adjunta después (si no, queda con evidencia pendiente).
 *
 * @param metodo     medio de pago
 * @param valor      valor pagado (mayor que cero, 2 decimales)
 * @param fechaPago  fecha en que se pagó (hoy o antes)
 * @param referencia número de transferencia, cheque o similar (opcional)
 * @param tipo       ANTICIPO (antes de recibir, con tope) o PAGO_FINAL (contra una factura que cuadre con lo recibido)
 */
public record RegistrarPagoRequest(
        @NotNull(message = "es obligatorio")
        MetodoPago metodo,

        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 16 enteros y 2 decimales")
        BigDecimal valor,

        @NotNull(message = "es obligatoria")
        @PastOrPresent(message = "no puede ser una fecha futura")
        LocalDate fechaPago,

        @Size(max = 100, message = "máximo 100 caracteres")
        String referencia,

        @NotNull(message = "es obligatorio")
        TipoPago tipo) {
}
