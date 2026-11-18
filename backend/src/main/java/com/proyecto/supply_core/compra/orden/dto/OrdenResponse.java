package com.proyecto.supply_core.compra.orden.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.compra.orden.entity.OrdenCompra;
import com.proyecto.supply_core.compra.orden.entity.OrdenEstadoHistorial;
import com.proyecto.supply_core.compra.orden.enums.EstadoOrden;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Orden de compra con líneas e historial.
 *
 * @param id                   id (de esta versión)
 * @param consecutivo          OC-000001
 * @param version              número de versión
 * @param ordenPadreId         versión anterior
 * @param motivoVersion        motivo del cambio de versión
 * @param estado               estado
 * @param adjudicacionId       adjudicación de origen ({@code null} en una orden directa de contrato)
 * @param contratoId           contrato de origen (solo en una orden directa)
 * @param solicitudId          solicitud de origen (solo en una orden directa)
 * @param proveedorId          proveedor
 * @param proveedorRazonSocial razón social
 * @param bodegaDestinoId      bodega destino
 * @param bodegaDestinoNombre  nombre de la bodega
 * @param lugarEntrega         lugar de entrega
 * @param fechaEntregaEsperada fecha comprometida
 * @param subtotal             suma de netos
 * @param impuestos            suma de impuestos
 * @param total                subtotal + impuestos
 * @param creadoEn             fecha de emisión de esta versión
 * @param lineas               productos
 * @param historial            cambios de estado
 */
public record OrdenResponse(Long id, String consecutivo, int version, Long ordenPadreId, String motivoVersion,
        EstadoOrden estado, Long adjudicacionId, Long contratoId, Long solicitudId, Long proveedorId,
        String proveedorRazonSocial, Long bodegaDestinoId, String bodegaDestinoNombre, String lugarEntrega, LocalDate fechaEntregaEsperada, BigDecimal subtotal,
        BigDecimal impuestos, BigDecimal total, LocalDateTime creadoEn, List<Linea> lineas, List<Cambio> historial) {

    /**
     * Convierte la entidad (detalle cargado).
     *
     * @param o         orden
     * @param historial cambios de estado
     * @return DTO
     */
    public static OrdenResponse of(OrdenCompra o, List<OrdenEstadoHistorial> historial) {
        return new OrdenResponse(o.getId(), o.getConsecutivo(), o.getVersion(), o.getOrdenPadreId(), o.getMotivoVersion(),
                o.getEstado(), o.getAdjudicacionId(), o.getContratoId(), o.getSolicitudId(), o.getProveedor().getId(),
                o.getProveedor().getRazonSocial(), o.getBodegaDestino().getId(), o.getBodegaDestino().getNombre(),
                o.getLugarEntrega(),
                o.getFechaEntregaEsperada(), o.getSubtotal(), o.getImpuestos(), o.getTotal(), o.getCreadoEn(),
                o.getLineas().stream().map(l -> new Linea(l.getId(), l.getProducto().getId(), l.getProducto().getCodigo(),
                        l.getProducto().getNombre(), l.getProducto().getUnidadMedida().getCodigo(), l.getCantidad(),
                        l.getPrecioUnitario(), l.getImpuestoPct(), l.getSubtotal())).toList(),
                historial.stream().map(h -> new Cambio(h.getEstado(), h.getUsuarioId(), h.getObservacion(), h.getCreadoEn()))
                        .toList());
    }

    /**
     * Línea de la orden (unidad del producto).
     *
     * @param id             línea
     * @param productoId     producto
     * @param productoCodigo código
     * @param productoNombre nombre
     * @param unidad         unidad del producto
     * @param cantidad       cantidad
     * @param precioUnitario precio sin impuesto
     * @param impuestoPct    impuesto %
     * @param subtotal       neto
     */
    @Schema(name = "OrdenLinea")
    public record Linea(Long id, Long productoId, String productoCodigo, String productoNombre, String unidad,
            BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal impuestoPct, BigDecimal subtotal) {
    }

    /**
     * Cambio de estado.
     *
     * @param estado      estado
     * @param usuarioId   quién
     * @param observacion comentario
     * @param fecha       cuándo
     */
    public record Cambio(String estado, Long usuarioId, String observacion, LocalDateTime fecha) {
    }
}
