package com.proyecto.supply_core.reorden.dto;

import java.time.LocalDateTime;

/**
 * Una orden que traía el producto y cuándo llegó su primera entrega. Sirve para medir cuánto tardan de verdad los
 * proveedores.
 *
 * @param productoId producto de la orden
 * @param pedidoEn   cuándo se emitió la orden (o su versión vigente)
 * @param recibidoEn primera recepción de esa orden
 */
public record EntregaHistorica(Long productoId, LocalDateTime pedidoEn, LocalDateTime recibidoEn) {
}
