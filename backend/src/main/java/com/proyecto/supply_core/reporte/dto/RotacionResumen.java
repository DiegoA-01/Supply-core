package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;

/**
 * Totales del reporte de rotación y los umbrales con que se clasificó (para explicarlos en pantalla).
 *
 * @param productos            productos activos analizados
 * @param conSalidas           productos que tuvieron salidas en el período
 * @param sinMovimiento        productos con stock y sin salidas en el período
 * @param agotados             productos que se movieron y hoy no tienen stock
 * @param porAgotarse          productos cuyo stock alcanza para pocos días
 * @param valorInventario      suma de stock × costo de los productos que tienen costo
 * @param valorSinMovimiento   parte de ese valor que está en productos sin movimiento (dinero parado)
 * @param diasRotacionAlta     rotación ALTA: el stock alcanza este número de días o menos
 * @param diasRotacionBaja     rotación BAJA: el stock alcanza más de este número de días
 * @param diasPorAgotarse      "por agotarse": el stock alcanza este número de días o menos
 */
public record RotacionResumen(int productos, long conSalidas, long sinMovimiento, long agotados, long porAgotarse,
        BigDecimal valorInventario, BigDecimal valorSinMovimiento, int diasRotacionAlta, int diasRotacionBaja,
        int diasPorAgotarse) {
}
