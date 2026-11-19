package com.proyecto.supply_core.catalogoproveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.catalogoproveedor.entity.CatalogoItem;

/**
 * Lo que el proveedor ve de su propio catálogo: sin equivalencias ni datos del inventario de la empresa.
 *
 * @param codigoProveedor código
 * @param descripcion     descripción
 * @param marca           marca
 * @param unidad          unidad
 * @param precio          precio
 * @param incluyeIva      si el precio trae el IVA
 * @param vigenteHasta    hasta cuándo rige el precio
 * @param activo          si vino en el último archivo
 * @param actualizadoEn   última carga
 */
public record PortalCatalogoItem(String codigoProveedor, String descripcion, String marca, String unidad,
        BigDecimal precio, boolean incluyeIva, LocalDate vigenteHasta, boolean activo, LocalDateTime actualizadoEn) {

    /**
     * @param c artículo
     * @return vista del proveedor
     */
    public static PortalCatalogoItem of(CatalogoItem c) {
        return new PortalCatalogoItem(c.getCodigoProveedor(), c.getDescripcion(), c.getMarca(), c.getUnidad(),
                c.getPrecio(), c.isIncluyeIva(), c.getVigenteHasta(), c.isActivo(), c.getActualizadoEn());
    }
}
