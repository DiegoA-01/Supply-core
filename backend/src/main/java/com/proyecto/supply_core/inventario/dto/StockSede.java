package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;

/**
 * Stock de un producto sumado en las bodegas de una sede.
 *
 * @param sedeId   sede
 * @param sede     nombre de la sede
 * @param cantidad total en sus bodegas
 */
public record StockSede(Long sedeId, String sede, BigDecimal cantidad) {
}
