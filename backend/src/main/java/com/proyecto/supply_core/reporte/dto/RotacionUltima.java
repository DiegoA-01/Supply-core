package com.proyecto.supply_core.reporte.dto;

import java.time.LocalDateTime;

/**
 * Fila agregada que devuelve la base de datos: la última vez que salió un producto.
 *
 * @param productoId producto
 * @param ultima     fecha y hora de su última salida
 */
public record RotacionUltima(Long productoId, LocalDateTime ultima) {
}
