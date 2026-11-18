package com.proyecto.supply_core.recepcion.dto;

import java.math.BigDecimal;
import java.util.List;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

/**
 * Estado de la recepción de una orden: por línea cuánto se pidió, se aceptó, se rechazó y falta, y las
 * recepciones registradas.
 *
 * @param ordenId          orden
 * @param ordenConsecutivo OC-000001
 * @param estadoOrden      estado actual de la orden
 * @param lineas           avance por línea de la orden
 * @param recepciones      recepciones, de la más antigua a la más reciente
 */
public record ResumenRecepcionResponse(Long ordenId, String ordenConsecutivo, EstadoOrden estadoOrden,
        List<Avance> lineas, List<RecepcionResponse> recepciones) {

    /**
     * Avance de una línea de la orden.
     *
     * @param ordenLineaId       línea de la orden
     * @param productoCodigo     código del producto
     * @param productoNombre     nombre del producto
     * @param pedido             cantidad pedida
     * @param aceptado           lo aceptado hasta ahora (ya en el stock)
     * @param rechazado          lo rechazado hasta ahora
     * @param faltante           lo que aún falta para completar el pedido
     * @param excedentePendiente lo que llegó de más y espera decisión de compras
     */
    public record Avance(Long ordenLineaId, String productoCodigo, String productoNombre, BigDecimal pedido,
            BigDecimal aceptado, BigDecimal rechazado, BigDecimal faltante, BigDecimal excedentePendiente) {
    }
}
