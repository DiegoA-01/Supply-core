package com.proyecto.supply_core.cartera.dto;

import java.util.List;

/**
 * Cartera de un cliente con sus cuentas y abonos.
 *
 * @param cliente cartera del cliente
 * @param cuentas cuentas por cobrar
 * @param abonos  abonos
 */
public record DetalleCartera(ClienteCartera cliente, List<CuentaResponse> cuentas, List<AbonoResponse> abonos) {
}
