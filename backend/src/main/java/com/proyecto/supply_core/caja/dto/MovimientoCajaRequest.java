package com.proyecto.supply_core.caja.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.caja.enums.TipoMovimientoCaja;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Retiro, gasto o ingreso de efectivo en el turno (los tres con autorización de un supervisor).
 *
 * @param tipo         RETIRO, GASTO o INGRESO
 * @param monto        monto en positivo; el sistema lo guarda en negativo si sale
 * @param motivo       para qué
 * @param soporte      número del recibo o factura (gastos), opcional
 * @param autorizacion token de un solo uso que devolvió {@code POST /api/autorizaciones}
 */
public record MovimientoCajaRequest(
        @NotNull(message = "es obligatorio") TipoMovimientoCaja tipo,
        @NotNull(message = "es obligatorio")
        @DecimalMin(value = "0.01", message = "debe ser mayor que cero")
        @Digits(integer = 16, fraction = 2, message = "máximo 2 decimales")
        BigDecimal monto,
        @NotBlank(message = "es obligatorio") @Size(max = 300, message = "máximo 300 caracteres") String motivo,
        @Size(max = 100, message = "máximo 100 caracteres") String soporte,
        @NotBlank(message = "pida la autorización del supervisor") @Size(max = 100, message = "no es válida")
        String autorizacion) {
}
