package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Totales del carrito calculados en Java, sin guardar nada. La pantalla los muestra tal cual.
 *
 * @param lineas                          líneas con su precio y total
 * @param subtotal                        suma de precios de lista
 * @param descuentoTotal                  suma de descuentos
 * @param baseGravable                    suma de bases
 * @param impuestos                       suma de impuestos
 * @param total                           total de la venta
 * @param redondeo                        ajuste del efectivo a $50 (si se paga todo en efectivo o aún no hay pagos)
 * @param totalAPagar                     lo que se cobra
 * @param pagado                          suma de los pagos escritos
 * @param cambio                          cambio ({@code null} si los pagos aún no cuadran)
 * @param mensajePago                     por qué los pagos no cuadran ({@code null} si cuadran)
 * @param limiteDescuentoPct              descuento máximo de la sesión sin autorización
 * @param requiereAutorizacionDescuento   {@code true} si algún descuento pasa el límite
 * @param requiereAutorizacionPrecio      {@code true} si algún precio queda bajo el mínimo
 * @param creditoDisponible               cupo disponible del cliente ({@code null} = consumidor final)
 * @param saldoFavor                      saldo a favor del cliente
 * @param creditoBloqueado                {@code true} si está en mora sin excepción (no compra a crédito)
 */
public record CotizacionVenta(List<LineaCotizada> lineas, BigDecimal subtotal, BigDecimal descuentoTotal,
        BigDecimal baseGravable, BigDecimal impuestos, BigDecimal total, BigDecimal redondeo, BigDecimal totalAPagar,
        BigDecimal pagado, BigDecimal cambio, String mensajePago, BigDecimal limiteDescuentoPct,
        boolean requiereAutorizacionDescuento, boolean requiereAutorizacionPrecio, BigDecimal creditoDisponible,
        BigDecimal saldoFavor, boolean creditoBloqueado) {
}
