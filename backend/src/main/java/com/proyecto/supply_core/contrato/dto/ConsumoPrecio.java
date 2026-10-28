package com.proyecto.supply_core.contrato.dto;

import java.math.BigDecimal;

/**
 * Cantidad ya pedida contra un precio pactado (resultado de la consulta de consumo del contrato).
 *
 * @param precioId   precio pactado ({@code contrato_precio.id})
 * @param consumido  suma de las cantidades de las órdenes vigentes de ese precio, en unidades del producto
 */
public record ConsumoPrecio(Long precioId, BigDecimal consumido) {
}
