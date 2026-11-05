package com.proyecto.supply_core.inventario.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

/**
 * Filtros opcionales del kardex (query params); los presentes se combinan con AND.
 *
 * @param productoId producto
 * @param bodegaId   bodega (como origen o como destino)
 * @param tipo       tipo de movimiento
 * @param desde      fecha/hora inicial inclusive (ISO)
 * @param hasta      fecha/hora final inclusive (ISO)
 */
public record MovimientoFiltro(
        @Positive(message = "debe ser un id válido") Long productoId,
        @Positive(message = "debe ser un id válido") Long bodegaId,
        TipoMovimiento tipo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {

    /**
     * Regla entre campos: el rango de fechas no puede estar invertido.
     *
     * @return {@code true} si falta alguna fecha o {@code desde <= hasta}
     */
    @AssertTrue(message = "la fecha 'desde' no puede ser posterior a 'hasta'")
    public boolean isRangoValido() {
        return desde == null || hasta == null || !desde.isAfter(hasta);
    }
}
