package com.proyecto.supply_core.catalogoproveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;
import com.proyecto.supply_core.catalogoproveedor.enums.EstadoEquivalencia;

/**
 * Artículo de un catálogo como lo ve compras, con su equivalencia en el inventario.
 *
 * @param id                 id
 * @param proveedorId        proveedor
 * @param proveedor          razón social
 * @param codigoProveedor    código del proveedor
 * @param descripcion        descripción
 * @param marca              marca
 * @param unidad             unidad del proveedor
 * @param precio             precio
 * @param incluyeIva         si el precio trae el IVA
 * @param vigenteHasta       vigencia del precio
 * @param productoId         producto equivalente o {@code null}
 * @param producto           código y nombre del producto
 * @param unidadProducto     unidad del producto en el inventario
 * @param estadoEquivalencia estado de la equivalencia
 * @param similitud          parecido calculado, de 0 a 1
 * @param actualizadoEn      última carga del proveedor
 */
public record CatalogoItemResponse(Long id, Long proveedorId, String proveedor, String codigoProveedor,
        String descripcion, String marca, String unidad, BigDecimal precio, boolean incluyeIva, LocalDate vigenteHasta,
        Long productoId, String producto, String unidadProducto, EstadoEquivalencia estadoEquivalencia,
        BigDecimal similitud, LocalDateTime actualizadoEn) {

    /**
     * @param c artículo (con proveedor y producto cargados)
     * @return DTO
     */
    public static CatalogoItemResponse of(CatalogoItem c) {
        var p = c.getProducto();
        return new CatalogoItemResponse(c.getId(), c.getProveedor().getId(), c.getProveedor().getRazonSocial(),
                c.getCodigoProveedor(), c.getDescripcion(), c.getMarca(), c.getUnidad(), c.getPrecio(), c.isIncluyeIva(),
                c.getVigenteHasta(), p == null ? null : p.getId(), p == null ? null : p.getCodigo() + " - " + p.getNombre(),
                p == null ? null : p.getUnidadMedida().getCodigo(), c.getEstadoEquivalencia(), c.getSimilitud(),
                c.getActualizadoEn());
    }
}
