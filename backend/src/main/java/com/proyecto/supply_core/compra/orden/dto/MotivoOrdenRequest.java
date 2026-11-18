package com.proyecto.supply_core.compra.orden.dto;

import jakarta.validation.constraints.Size;

/**
 * Comentario de un cambio de estado (obligatorio al rechazar o cancelar; lo verifica el Service).
 *
 * @param observacion motivo o comentario (p. ej. número de guía al despachar)
 */
public record MotivoOrdenRequest(@Size(max = 500, message = "máximo 500 caracteres") String observacion) {
}
