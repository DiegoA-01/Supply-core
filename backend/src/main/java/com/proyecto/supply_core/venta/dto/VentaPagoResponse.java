package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.venta.enums.MedioPagoVenta;

/**
 * Pago de una venta.
 *
 * @param medio      medio
 * @param monto      lo que quedó en la caja por este medio
 * @param recibido   en efectivo, lo que entregó el cliente
 * @param referencia autorización o número de transferencia
 * @param ultimos4   últimos 4 dígitos de la tarjeta
 */
public record VentaPagoResponse(MedioPagoVenta medio, BigDecimal monto, BigDecimal recibido, String referencia,
        String ultimos4) {
}
