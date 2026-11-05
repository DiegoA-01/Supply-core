package com.proyecto.supply_core.compra.rfq.dto;

import java.math.BigDecimal;

import com.proyecto.supply_core.compra.rfq.entity.RfqLinea;

/**
 * Producto a cotizar.
 *
 * @param id               id de la línea (la cotización del proveedor la referencia)
 * @param productoId       producto
 * @param productoCodigo   código
 * @param productoNombre   nombre
 * @param unidadMedidaId   unidad pedida
 * @param unidadCodigo     código de la unidad
 * @param cantidad         cantidad pedida
 * @param especificaciones detalle técnico
 */
public record RfqLineaResponse(Long id, Long productoId, String productoCodigo, String productoNombre,
        Long unidadMedidaId, String unidadCodigo, BigDecimal cantidad, String especificaciones) {

    /**
     * Convierte la entidad (producto y unidad cargados).
     *
     * @param l línea
     * @return DTO
     */
    public static RfqLineaResponse of(RfqLinea l) {
        return new RfqLineaResponse(l.getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                l.getProducto().getNombre(), l.getUnidadMedida().getId(), l.getUnidadMedida().getCodigo(),
                l.getCantidad(), l.getEspecificaciones());
    }
}
