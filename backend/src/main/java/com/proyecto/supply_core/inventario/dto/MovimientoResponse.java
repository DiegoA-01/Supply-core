package com.proyecto.supply_core.inventario.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.proyecto.supply_core.inventario.entity.Bodega;
import com.proyecto.supply_core.inventario.entity.MovimientoInventario;
import com.proyecto.supply_core.inventario.enums.TipoMovimiento;

/**
 * Movimiento del kardex tal como lo ve la API.
 *
 * @param id                     id
 * @param tipo                   tipo de movimiento
 * @param productoId             producto
 * @param productoCodigo         código del producto
 * @param productoNombre         nombre del producto
 * @param bodegaOrigenId         bodega de origen o {@code null}
 * @param bodegaOrigenNombre     nombre de la bodega de origen o {@code null}
 * @param bodegaDestinoId        bodega de destino o {@code null}
 * @param bodegaDestinoNombre    nombre de la bodega de destino o {@code null}
 * @param cantidad               cantidad movida
 * @param stockResultante        saldo de la bodega afectada (en traslados, la de origen)
 * @param stockResultanteDestino en traslados, saldo de la bodega de destino
 * @param motivo                 motivo
 * @param referenciaTipo         origen automático (p. ej. RECEPCION) o {@code null}
 * @param referenciaId           id del documento de origen o {@code null}
 * @param usuarioId              quién lo registró
 * @param creadoEn               cuándo
 */
public record MovimientoResponse(Long id, TipoMovimiento tipo, Long productoId, String productoCodigo,
        String productoNombre, Long bodegaOrigenId, String bodegaOrigenNombre, Long bodegaDestinoId,
        String bodegaDestinoNombre, BigDecimal cantidad, BigDecimal stockResultante,
        BigDecimal stockResultanteDestino, String motivo, String referenciaTipo, Long referenciaId,
        Long usuarioId, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO (requiere producto y bodegas cargados).
     *
     * @param m movimiento
     * @return DTO
     */
    public static MovimientoResponse of(MovimientoInventario m) {
        Bodega o = m.getBodegaOrigen();
        Bodega d = m.getBodegaDestino();
        return new MovimientoResponse(m.getId(), m.getTipo(), m.getProducto().getId(), m.getProducto().getCodigo(),
                m.getProducto().getNombre(), o == null ? null : o.getId(), o == null ? null : o.getNombre(),
                d == null ? null : d.getId(), d == null ? null : d.getNombre(), m.getCantidad(),
                m.getStockResultante(), m.getStockResultanteDestino(), m.getMotivo(), m.getReferenciaTipo(),
                m.getReferenciaId(), m.getUsuarioId(), m.getCreadoEn());
    }
}
