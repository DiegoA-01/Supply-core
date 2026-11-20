package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.venta.enums.EstadoVenta;

/**
 * Venta con sus líneas y pagos, lista para el tiquete.
 *
 * @param id               id
 * @param consecutivo      consecutivo de la caja ({@code C1-000123})
 * @param estado           estado
 * @param cajaId           caja
 * @param caja             nombre de la caja
 * @param turnoId          turno
 * @param clienteId        cliente
 * @param cliente          nombre del cliente
 * @param clienteDocumento documento del cliente
 * @param cajero           nombre del cajero
 * @param vendedor         nombre del vendedor ({@code null} si no hubo)
 * @param lista            lista de precios usada
 * @param pedidoVentaId    pedido cobrado ({@code null} si fue venta directa)
 * @param subtotal         suma de precios de lista
 * @param descuentoTotal   suma de descuentos
 * @param baseGravable     suma de bases
 * @param impuestos        suma de impuestos
 * @param total            total de la venta
 * @param redondeo         ajuste del redondeo del efectivo
 * @param totalAPagar      lo que pagó el cliente
 * @param cambio           cambio entregado
 * @param confirmadaEn     fecha y hora
 * @param autorizadaPor    supervisor que autorizó algo de la venta
 * @param anuladaEn        fecha de anulación ({@code null} si sigue confirmada)
 * @param motivoAnulacion  motivo de la anulación
 * @param lineas           líneas
 * @param pagos            pagos
 * @param turnoAbierto     {@code true} si su turno sigue abierto (solo así se anula)
 * @param totalDevuelto    total ya devuelto al cliente
 */
public record VentaResponse(Long id, String consecutivo, EstadoVenta estado, Long cajaId, String caja, Long turnoId,
        Long clienteId, String cliente, String clienteDocumento, String cajero, String vendedor, String lista,
        Long pedidoVentaId, BigDecimal subtotal, BigDecimal descuentoTotal, BigDecimal baseGravable,
        BigDecimal impuestos, BigDecimal total, BigDecimal redondeo, BigDecimal totalAPagar, BigDecimal cambio,
        LocalDateTime confirmadaEn, String autorizadaPor, LocalDateTime anuladaEn, String motivoAnulacion,
        List<VentaLineaResponse> lineas, List<VentaPagoResponse> pagos, boolean turnoAbierto, BigDecimal totalDevuelto) {
}
