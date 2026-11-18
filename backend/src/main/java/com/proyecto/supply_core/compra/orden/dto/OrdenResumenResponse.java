package com.proyecto.supply_core.compra.orden.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

/**
 * Orden para listados.
 *
 * @param id                   id
 * @param consecutivo          OC-000001
 * @param version              versión
 * @param estado               estado
 * @param proveedorRazonSocial proveedor
 * @param bodegaDestinoNombre  bodega destino
 * @param fechaEntregaEsperada fecha comprometida
 * @param total                total
 */
public record OrdenResumenResponse(Long id, String consecutivo, int version, EstadoOrden estado,
        String proveedorRazonSocial, String bodegaDestinoNombre, LocalDate fechaEntregaEsperada, BigDecimal total) {

    /**
     * Convierte la entidad.
     *
     * @param o orden con proveedor y bodega
     * @return DTO
     */
    public static OrdenResumenResponse of(OrdenCompra o) {
        return new OrdenResumenResponse(o.getId(), o.getConsecutivo(), o.getVersion(), o.getEstado(),
                o.getProveedor().getRazonSocial(), o.getBodegaDestino().getNombre(), o.getFechaEntregaEsperada(),
                o.getTotal());
    }
}
