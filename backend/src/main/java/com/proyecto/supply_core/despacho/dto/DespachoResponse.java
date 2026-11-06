package com.proyecto.supply_core.despacho.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.despacho.entity.Despacho;
import com.proyecto.supply_core.despacho.entity.DespachoLinea;
import com.proyecto.supply_core.despacho.enums.EstadoDespacho;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Detalle de un despacho.
 *
 * @param id                  id
 * @param consecutivo         DES-000001
 * @param estado              estado
 * @param bodegaId            bodega de salida
 * @param bodegaNombre        nombre de la bodega
 * @param usuarioId           quién lo creó
 * @param cliente             cliente o destinatario
 * @param referenciaExterna   factura o ticket externo
 * @param observaciones       notas
 * @param confirmadoEn        fecha de confirmación
 * @param anuladoEn           fecha de anulación
 * @param motivoAnulacion     motivo de la anulación
 * @param creadoEn            fecha de creación
 * @param lineas              productos y cantidades
 * @param productosBajoMinimo códigos de productos que quedaron bajo su stock mínimo al confirmar
 */
public record DespachoResponse(Long id, String consecutivo, EstadoDespacho estado, Long bodegaId, String bodegaNombre,
        Long usuarioId, String cliente, String referenciaExterna, String observaciones, LocalDateTime confirmadoEn,
        LocalDateTime anuladoEn, String motivoAnulacion, LocalDateTime creadoEn, List<Linea> lineas,
        List<String> productosBajoMinimo) {

    /**
     * Convierte la entidad (detalle cargado).
     *
     * @param d                   despacho
     * @param productosBajoMinimo alertas de stock (vacío si no aplica)
     * @return DTO
     */
    public static DespachoResponse of(Despacho d, List<String> productosBajoMinimo) {
        return new DespachoResponse(d.getId(), d.getConsecutivo(), d.getEstado(), d.getBodega().getId(),
                d.getBodega().getNombre(), d.getUsuarioId(), d.getCliente(), d.getReferenciaExterna(),
                d.getObservaciones(), d.getConfirmadoEn(), d.getAnuladoEn(), d.getMotivoAnulacion(), d.getCreadoEn(),
                d.getLineas().stream().map(Linea::of).toList(), productosBajoMinimo);
    }

    /**
     * Línea del despacho.
     *
     * @param id                   id
     * @param productoId           producto
     * @param productoCodigo       código
     * @param productoNombre       nombre
     * @param unidadMedidaId       unidad de la línea
     * @param unidadCodigo         código de la unidad de la línea
     * @param cantidad             cantidad en esa unidad
     * @param cantidadBase         cantidad descontada en la unidad del producto (al confirmar)
     * @param unidadProductoCodigo unidad del producto (la del stock)
     */
    @Schema(name = "DespachoLinea")
    public record Linea(Long id, Long productoId, String productoCodigo, String productoNombre, Long unidadMedidaId,
            String unidadCodigo, BigDecimal cantidad, BigDecimal cantidadBase, String unidadProductoCodigo) {

        /**
         * Convierte la entidad (producto y unidades cargados).
         *
         * @param l línea
         * @return DTO
         */
        static Linea of(DespachoLinea l) {
            return new Linea(l.getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                    l.getProducto().getNombre(), l.getUnidadMedida().getId(), l.getUnidadMedida().getCodigo(),
                    l.getCantidad(), l.getCantidadBase(), l.getProducto().getUnidadMedida().getCodigo());
        }
    }
}
