package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

/**
 * Una orden en el estado de cuenta del proveedor.
 *
 * @param consecutivo OC-000001
 * @param ordenId     versión vigente
 * @param fecha       fecha de emisión
 * @param estado      estado de la orden
 * @param total       total de la orden
 * @param facturado   total de la factura vigente; 0 si no hay
 * @param pagado      pagos REGISTRADOS
 * @param saldo       (facturado si hay factura, si no el total de la orden) − pagado
 */
public record EstadoCuentaOrden(String consecutivo, Long ordenId, LocalDate fecha, EstadoOrden estado,
        BigDecimal total, BigDecimal facturado, BigDecimal pagado, BigDecimal saldo) {
}
