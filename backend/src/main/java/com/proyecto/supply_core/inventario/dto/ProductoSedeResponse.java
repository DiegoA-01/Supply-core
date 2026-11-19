package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

/**
 * Situación de un producto en una sede: su stock y sus límites propios.
 *
 * @param sedeId      sede
 * @param sede        nombre
 * @param stock       stock en las bodegas de la sede
 * @param stockMinimo mínimo propio de la sede (0 si no tiene)
 * @param stockMaximo máximo propio o {@code null}
 * @param configurado {@code true} si la sede tiene límites propios para el producto
 * @param bajoMinimo  {@code true} si tiene mínimo propio y el stock está por debajo
 */
public record ProductoSedeResponse(Long sedeId, String sede, BigDecimal stock, BigDecimal stockMinimo,
        BigDecimal stockMaximo, boolean configurado, boolean bajoMinimo) {
}
