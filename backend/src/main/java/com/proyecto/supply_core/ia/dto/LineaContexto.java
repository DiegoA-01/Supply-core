package com.proyecto.supply_core.ia.dto;

import java.math.BigDecimal;

/**
 * Producto pedido en la RFQ, que se le muestra a la IA para que relacione cada renglón del documento.
 *
 * @param rfqLineaId       id de la línea de la RFQ (la IA lo devuelve en cada renglón)
 * @param codigo           código del producto
 * @param nombre           nombre del producto
 * @param cantidad         cantidad pedida
 * @param unidad           unidad pedida (código)
 * @param especificaciones detalle técnico pedido
 */
public record LineaContexto(Long rfqLineaId, String codigo, String nombre, BigDecimal cantidad, String unidad,
        String especificaciones) {
}
