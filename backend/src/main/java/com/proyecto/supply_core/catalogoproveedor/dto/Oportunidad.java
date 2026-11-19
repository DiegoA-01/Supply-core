package com.proyecto.supply_core.catalogoproveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.proyecto.supply_core.reorden.enums.EstadoReabastecimiento;

/**
 * Oportunidad de compra para el jefe de compras: un producto que se consigue más barato en un catálogo o que hay que
 * pedir pronto y tiene oferta. Todas las cifras las calcula Java.
 *
 * @param productoId             producto
 * @param codigo                 código
 * @param nombre                 nombre
 * @param unidad                 unidad del producto
 * @param proveedorId            proveedor con la mejor oferta
 * @param proveedor              razón social
 * @param codigoProveedor        código en su catálogo
 * @param descripcionProveedor   cómo lo llama el proveedor
 * @param marca                  marca ofrecida
 * @param precioCatalogo         precio como viene en el catálogo
 * @param precioSinIva           precio comparable con el costo (sin IVA)
 * @param costoActual            último costo de compra
 * @param ahorroPct              cuánto más barato que el último costo, en % (negativo = más caro)
 * @param unidadComparable       {@code false} si la unidad del proveedor no coincide con la del producto
 * @param ofertas                cuántos proveedores lo ofrecen
 * @param estadoReabastecimiento qué tan pronto hay que pedirlo
 * @param cantidadSugerida       cuánto sugiere el plan de reabastecimiento
 * @param pedirAntesDe           fecha límite del plan
 * @param motivo                 explicación para el jefe de compras
 */
public record Oportunidad(Long productoId, String codigo, String nombre, String unidad, Long proveedorId,
        String proveedor, String codigoProveedor, String descripcionProveedor, String marca, BigDecimal precioCatalogo,
        BigDecimal precioSinIva, BigDecimal costoActual, BigDecimal ahorroPct, boolean unidadComparable, int ofertas,
        EstadoReabastecimiento estadoReabastecimiento, BigDecimal cantidadSugerida, LocalDate pedirAntesDe,
        String motivo) {
}
