package com.proyecto.supply_core.compra.solicitud.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompraLinea;

/**
 * Línea de una solicitud.
 *
 * @param id              id
 * @param productoId      producto
 * @param productoCodigo  código del producto
 * @param productoNombre  nombre del producto
 * @param unidadMedidaId  unidad de la línea
 * @param unidadCodigo    código de la unidad
 * @param cantidad        cantidad en esa unidad
 * @param costoReferencia costo de referencia del producto (por su unidad); {@code null} si no tiene
 */
public record LineaSolicitudResponse(Long id, Long productoId, String productoCodigo, String productoNombre,
        Long unidadMedidaId, String unidadCodigo, BigDecimal cantidad, BigDecimal costoReferencia) {

    /**
     * Convierte la entidad (producto y unidad deben estar cargados).
     *
     * @param l línea
     * @return DTO
     */
    public static LineaSolicitudResponse of(SolicitudCompraLinea l) {
        return new LineaSolicitudResponse(l.getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                l.getProducto().getNombre(), l.getUnidadMedida().getId(), l.getUnidadMedida().getCodigo(),
                l.getCantidad(), l.getProducto().getCostoReferencia());
    }
}
