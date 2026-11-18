package com.proyecto.supply_core.reporte.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Rotación de los productos en un período: los que más salen primero.
 *
 * @param desde   primer día del período
 * @param hasta   último día del período (incluido)
 * @param dias    días del período
 * @param resumen totales y umbrales
 * @param items   productos, de más a menos salidas (a igual salida, por nombre)
 */
public record RotacionResponse(LocalDate desde, LocalDate hasta, int dias, RotacionResumen resumen,
        List<RotacionItem> items) {
}
