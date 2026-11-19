package com.proyecto.supply_core.pedido.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.pedido.enums.EstadoPedidoInterno;

/**
 * Pedido interno tal como lo ve la API.
 *
 * @param id             id
 * @param consecutivo    consecutivo (PED-000001)
 * @param usuarioId      quien lo pidió
 * @param solicitante    nombre de quien lo pidió
 * @param productoId     producto del catálogo o {@code null}
 * @param producto       código y nombre del producto o {@code null}
 * @param descripcion    descripción libre o {@code null}
 * @param cantidad       cantidad o {@code null}
 * @param unidad         unidad del producto o {@code null}
 * @param sedeId         sede o {@code null}
 * @param sede           nombre de la sede o {@code null}
 * @param nota           nota de quien pidió
 * @param estado         estado
 * @param respuesta      respuesta de compras
 * @param atendidoPor    nombre de quien respondió o {@code null}
 * @param atendidoEn     cuándo respondió compras o {@code null}
 * @param creadoEn       cuándo se pidió
 */
public record PedidoInternoResponse(Long id, String consecutivo, Long usuarioId, String solicitante, Long productoId,
        String producto, String descripcion, BigDecimal cantidad, String unidad, Long sedeId, String sede, String nota,
        EstadoPedidoInterno estado, String respuesta, String atendidoPor, LocalDateTime atendidoEn,
        LocalDateTime creadoEn) {
}
