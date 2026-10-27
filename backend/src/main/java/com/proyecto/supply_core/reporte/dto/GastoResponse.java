package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Gasto comprometido en órdenes vigentes (se excluyen canceladas, rechazadas por el proveedor y reemplazadas).
 *
 * @param desde         inicio del filtro (puede ser {@code null})
 * @param hasta         fin del filtro (puede ser {@code null})
 * @param ordenes       órdenes del período
 * @param subtotal      gasto sin impuesto
 * @param impuestos     impuesto de las líneas
 * @param total         subtotal + impuestos
 * @param pagado        pagos REGISTRADOS con fecha en el período; {@code null} si se filtró por categoría, porque
 *                      un pago no pertenece a una categoría
 * @param porProveedor  gasto por proveedor, de mayor a menor
 * @param porCategoria  gasto por categoría, de mayor a menor
 * @param porMes        gasto por mes de emisión, en orden cronológico
 */
public record GastoResponse(LocalDate desde, LocalDate hasta, long ordenes, BigDecimal subtotal, BigDecimal impuestos,
        BigDecimal total, BigDecimal pagado, List<GastoItem> porProveedor, List<GastoItem> porCategoria,
        List<GastoMes> porMes) {
}
