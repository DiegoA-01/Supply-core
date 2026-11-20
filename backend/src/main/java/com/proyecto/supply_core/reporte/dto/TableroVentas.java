package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Bloque de ventas del tablero del dueño (spec 009). Todas las cifras las calcula Java.
 *
 * @param hoy               día del cálculo
 * @param desdeMes          primer día del mes en curso
 * @param ventasHoy         ventas netas de hoy
 * @param numeroVentasHoy   ventas confirmadas hoy
 * @param ventasMes         ventas netas del mes
 * @param numeroVentasMes   ventas confirmadas en el mes
 * @param ticketPromedioMes ventas netas del mes / número de ventas ({@code null} sin ventas)
 * @param margenMes         margen del mes (base con costo − costo)
 * @param margenMesPct      margen del mes en %
 * @param masVendidos       productos que más vendieron en el mes (máximo {@value TableroResponse#MAXIMO_LISTA})
 * @param porMedio          ventas netas del mes por medio de pago
 * @param porCajero         ventas netas del mes por cajero
 * @param diferenciaCajaMes suma de las diferencias de los cierres del mes
 * @param faltantesMes      suma de los faltantes del mes
 * @param turnosPorAprobar  turnos del mes con diferencia sin aprobar
 */
public record TableroVentas(LocalDate hoy, LocalDate desdeMes, BigDecimal ventasHoy, long numeroVentasHoy,
        BigDecimal ventasMes, long numeroVentasMes, BigDecimal ticketPromedioMes, BigDecimal margenMes,
        BigDecimal margenMesPct, List<VentaReporte.Fila> masVendidos, List<VentaReporte.Fila> porMedio,
        List<VentaReporte.Fila> porCajero, BigDecimal diferenciaCajaMes, BigDecimal faltantesMes, long turnosPorAprobar) {
}
