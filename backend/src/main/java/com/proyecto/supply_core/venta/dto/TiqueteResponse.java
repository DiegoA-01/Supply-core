package com.proyecto.supply_core.venta.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos del tiquete de 80 mm (spec 005). Desde la segunda impresión dice COPIA.
 *
 * @param copia       {@code true} si es una reimpresión
 * @param impresion   número de impresión (1 = original)
 * @param impresoEn   fecha de esta impresión
 * @param empresa     razón social
 * @param nit         NIT de la empresa
 * @param direccion   dirección de la empresa
 * @param sede        sede de la caja
 * @param venta       venta con líneas y pagos
 * @param impuestos   impuestos discriminados por tarifa
 */
public record TiqueteResponse(boolean copia, int impresion, LocalDateTime impresoEn, String empresa, String nit,
        String direccion, String sede, VentaResponse venta, List<ImpuestoTiquete> impuestos) {
}
