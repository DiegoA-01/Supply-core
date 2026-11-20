package com.proyecto.supply_core.cartera.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.cartera.enums.EstadoCuenta;

/**
 * Cuenta por cobrar.
 *
 * @param id          id
 * @param ventaId     venta a crédito
 * @param venta       consecutivo de la venta
 * @param valor       valor original
 * @param saldo       saldo pendiente
 * @param venceEn     vencimiento
 * @param estado      estado
 * @param diasVencida días vencida (0 si aún no vence)
 */
public record CuentaResponse(Long id, Long ventaId, String venta, BigDecimal valor, BigDecimal saldo, LocalDate venceEn,
        EstadoCuenta estado, long diasVencida) {
}
