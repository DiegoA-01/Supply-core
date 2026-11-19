package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Producto encontrado al buscar para vender: lo que el cajero o el vendedor necesita, sin costos ni márgenes ni el
 * precio mínimo (esos los controla el backend al cobrar).
 *
 * @param productoId            producto
 * @param codigo                código
 * @param codigoBarras          código de barras o {@code null}
 * @param nombre                nombre
 * @param unidadMedidaId        unidad del producto
 * @param unidad                código de la unidad
 * @param porcentajeImpuesto    tarifa de impuesto o {@code null} si no tiene
 * @param precioIncluyeImpuesto si el precio ya trae el impuesto
 * @param precio                precio vigente en la unidad del producto, o {@code null} si no tiene
 * @param otrasUnidades         precios vigentes en otras unidades (por ejemplo la caja)
 * @param stock                 existencias en la bodega pedida (o en todas si no se pidió una)
 */
public record ProductoParaVender(Long productoId, String codigo, String codigoBarras, String nombre,
        Long unidadMedidaId, String unidad, BigDecimal porcentajeImpuesto, boolean precioIncluyeImpuesto,
        BigDecimal precio, List<PrecioEnUnidad> otrasUnidades, BigDecimal stock) {

    /**
     * Precio vigente en otra unidad.
     *
     * @param unidadMedidaId unidad
     * @param unidad         código de la unidad
     * @param precio         precio
     */
    public record PrecioEnUnidad(Long unidadMedidaId, String unidad, BigDecimal precio) {
    }
}
