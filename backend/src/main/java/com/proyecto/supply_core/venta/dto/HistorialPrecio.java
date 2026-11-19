package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un precio del historial.
 *
 * @param precio       precio
 * @param precioMinimo piso o {@code null}
 * @param vigenteDesde desde
 * @param vigenteHasta hasta ({@code null} = vigente)
 * @param usuario      quien lo fijó
 * @param motivo       por qué cambió
 */
public record HistorialPrecio(BigDecimal precio, BigDecimal precioMinimo, LocalDateTime vigenteDesde,
        LocalDateTime vigenteHasta, String usuario, String motivo) {
}
