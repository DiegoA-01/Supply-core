package com.proyecto.supply_core.cartera.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.caja.enums.MedioPago;

/**
 * Abono con su recibo.
 *
 * @param id            id
 * @param consecutivo   consecutivo ({@code AB-000001})
 * @param clienteId     cliente
 * @param cliente       nombre del cliente
 * @param documento     documento del cliente
 * @param valor         valor abonado
 * @param medioPago     medio
 * @param referencia    autorización o transferencia
 * @param usuario       quién lo recibió
 * @param creadoEn      fecha
 * @param aplicaciones  a qué cuentas se aplicó
 * @param saldoPendiente saldo que le queda al cliente después del abono
 */
public record AbonoResponse(Long id, String consecutivo, Long clienteId, String cliente, String documento,
        BigDecimal valor, MedioPago medioPago, String referencia, String usuario, LocalDateTime creadoEn,
        List<Aplicacion> aplicaciones, BigDecimal saldoPendiente) {

    /**
     * Parte del abono aplicada a una cuenta.
     *
     * @param cuentaId cuenta
     * @param venta    consecutivo de la venta de la cuenta
     * @param valor    valor aplicado
     */
    public record Aplicacion(Long cuentaId, String venta, BigDecimal valor) {
    }
}
