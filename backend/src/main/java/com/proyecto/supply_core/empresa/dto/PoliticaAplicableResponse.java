package com.proyecto.supply_core.empresa.dto;

/**
 * Resultado de consultar qué política aplica a un monto.
 *
 * @param requiereAprobacion {@code false} si ninguna política activa cubre el monto
 * @param politica           política que aplica o {@code null}
 */
public record PoliticaAplicableResponse(boolean requiereAprobacion, PoliticaAprobacionResponse politica) {
}
