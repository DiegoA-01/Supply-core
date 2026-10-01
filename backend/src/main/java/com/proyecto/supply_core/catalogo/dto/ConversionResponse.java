package com.proyecto.supply_core.catalogo.dto;

import java.math.BigDecimal;

/**
 * Resultado de convertir una cantidad entre dos unidades de la misma magnitud.
 *
 * @param cantidadOrigen cantidad recibida
 * @param unidadOrigen   código de la unidad de origen
 * @param resultado      cantidad equivalente (4 decimales, como las cantidades del sistema)
 * @param unidadDestino  código de la unidad de destino
 */
public record ConversionResponse(BigDecimal cantidadOrigen, String unidadOrigen, BigDecimal resultado,
        String unidadDestino) {
}
