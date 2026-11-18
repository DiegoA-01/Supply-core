package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un producto en el reporte de rotación. Todas las cifras las calcula el backend.
 *
 * @param productoId    producto
 * @param codigo        código del producto
 * @param nombre        nombre del producto
 * @param categoria     nombre de la categoría
 * @param unidad        código de la unidad de medida
 * @param stock         stock actual en todas las bodegas
 * @param stockMinimo   mínimo configurado
 * @param salidas       lo que salió en el período (consumo y despachos, menos despachos anulados)
 * @param comprado      lo que entró por compra en el período (menos devoluciones al proveedor)
 * @param consumoDiario salidas promedio por día en el período
 * @param diasCobertura días que alcanza el stock actual al ritmo del período; {@code null} si no hubo salidas
 * @param fechaEstimadaAgotamiento día en que se acabaría el stock al ritmo del período; {@code null} si no hubo salidas o no hay stock
 * @param ultimaSalida  última vez que salió (en cualquier fecha); {@code null} si nunca ha salido
 * @param diasSinSalida días desde esa última salida; {@code null} si nunca ha salido
 * @param valorStock    stock × costo de referencia; {@code null} si no hay costo
 * @param clase         clasificación de rotación
 * @param porAgotarse   {@code true} si el stock alcanza para pocos días al ritmo actual
 */
public record RotacionItem(Long productoId, String codigo, String nombre, String categoria, String unidad,
        BigDecimal stock, BigDecimal stockMinimo, BigDecimal salidas, BigDecimal comprado, BigDecimal consumoDiario,
        BigDecimal diasCobertura, LocalDate fechaEstimadaAgotamiento, LocalDateTime ultimaSalida, Long diasSinSalida, BigDecimal valorStock,
        ClaseRotacion clase, boolean porAgotarse) {
}
