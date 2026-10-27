package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Lo que la empresa le debe a un proveedor, orden por orden.
 *
 * @param proveedorId     proveedor
 * @param razonSocial     nombre
 * @param nit             NIT
 * @param desde           inicio del filtro por fecha de emisión (puede ser {@code null})
 * @param hasta           fin del filtro (puede ser {@code null})
 * @param totalOrdenado   suma de los totales de las órdenes
 * @param totalFacturado  suma de las facturas vigentes
 * @param totalPagado     suma de los pagos REGISTRADOS
 * @param saldo           suma de los saldos de cada orden
 * @param ordenes         órdenes vigentes, de la más reciente a la más antigua
 */
public record EstadoCuentaResponse(Long proveedorId, String razonSocial, String nit, LocalDate desde, LocalDate hasta,
        BigDecimal totalOrdenado, BigDecimal totalFacturado, BigDecimal totalPagado, BigDecimal saldo,
        List<EstadoCuentaOrden> ordenes) {
}
