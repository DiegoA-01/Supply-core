package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.proyecto.supply_core.reporte.enums.AgrupacionVenta;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Reporte de ventas netas de un periodo (spec 009). Todo lo calcula Java: la pantalla y la exportación solo lo muestran.
 *
 * @param desde     primer día (incluido)
 * @param hasta     último día (incluido)
 * @param agrupar   cómo se agrupan los renglones
 * @param filas     renglones (fechas en orden; lo demás, del que más vendió al que menos)
 * @param totales   totales del periodo, iguales en cualquier agrupación
 * @param soloPropio {@code true} si el reporte solo trae lo de quien lo pide (cajero o vendedor)
 */
public record VentaReporte(LocalDate desde, LocalDate hasta, AgrupacionVenta agrupar, List<Fila> filas, Totales totales,
        boolean soloPropio) {

    /**
     * Un renglón del reporte.
     *
     * @param clave         identificador del renglón (id, fecha o código del medio)
     * @param nombre        texto para mostrar
     * @param ventas        número de ventas confirmadas que aportan al renglón
     * @param unidades      unidades netas vendidas, en la unidad del producto ({@code null} por medio de pago)
     * @param bruto         lo vendido, con IVA
     * @param devoluciones  lo devuelto, con IVA
     * @param neto          bruto menos devoluciones
     * @param baseNeta      neto sin IVA ({@code null} por medio de pago)
     * @param costo         costo de lo vendido neto, con el costo guardado en cada línea ({@code null} por medio de pago)
     * @param margen        base neta con costo menos costo ({@code null} por medio de pago)
     * @param margenPct     margen sobre la base con costo, en % ({@code null} sin base o sin costo)
     * @param baseSinCosto  base de líneas que se vendieron sin costo conocido (no entra al margen)
     */
    @Schema(name = "FilaVentaReporte")
    public record Fila(String clave, String nombre, long ventas, BigDecimal unidades, BigDecimal bruto,
            BigDecimal devoluciones, BigDecimal neto, BigDecimal baseNeta, BigDecimal costo, BigDecimal margen,
            BigDecimal margenPct, BigDecimal baseSinCosto) {
    }

    /**
     * Totales del periodo.
     *
     * @param ventas          ventas confirmadas
     * @param bruto           lo vendido por líneas, con IVA
     * @param devoluciones    lo devuelto por líneas, con IVA
     * @param redondeo        ajuste del redondeo del efectivo cobrado menos el devuelto
     * @param neto            bruto − devoluciones + redondeo (lo que de verdad se cobró)
     * @param baseNeta        neto de las líneas sin IVA
     * @param costo           costo neto
     * @param margen          margen neto
     * @param margenPct       margen en %
     * @param baseSinCosto    base de líneas sin costo conocido
     * @param ticketPromedio  neto / ventas ({@code null} sin ventas)
     */
    @Schema(name = "TotalesVentaReporte")
    public record Totales(long ventas, BigDecimal bruto, BigDecimal devoluciones, BigDecimal redondeo, BigDecimal neto,
            BigDecimal baseNeta, BigDecimal costo, BigDecimal margen, BigDecimal margenPct, BigDecimal baseSinCosto,
            BigDecimal ticketPromedio) {
    }
}
