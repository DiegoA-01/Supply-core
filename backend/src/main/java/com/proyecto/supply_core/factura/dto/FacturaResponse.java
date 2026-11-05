package com.proyecto.supply_core.factura.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.factura.entity.FacturaProveedor;
import com.proyecto.supply_core.factura.enums.EstadoFactura;

/**
 * Factura del proveedor con el resultado del cruce de tres vías.
 *
 * @param id                   id
 * @param ordenId              orden a la que se facturó
 * @param ordenConsecutivo     OC-000001
 * @param proveedorId          proveedor
 * @param proveedorRazonSocial proveedor
 * @param usuarioId            quién la registró
 * @param numero               número de la factura
 * @param fechaFactura         fecha de la factura
 * @param subtotal             neto facturado
 * @param impuestos            impuestos facturados
 * @param total                total facturado
 * @param esperadoSubtotal     neto esperado según lo aceptado en la recepción
 * @param esperadoImpuestos    impuestos esperados
 * @param esperadoTotal        total esperado
 * @param diferencia           total facturado menos total esperado
 * @param estado               CUADRADA, EN_REVISION, APROBADA o ANULADA
 * @param documentoId          PDF de la factura, si se adjuntó
 * @param motivoAprobacion     motivo con el que compras aprobó la diferencia
 * @param aprobadoPorUsuarioId quién la aprobó
 * @param aprobadoEn           cuándo
 * @param motivoAnulacion      motivo, si se anuló
 * @param anuladoPorUsuarioId  quién la anuló
 * @param anuladoEn            cuándo
 * @param creadoEn             cuándo se registró
 */
public record FacturaResponse(Long id, Long ordenId, String ordenConsecutivo, Long proveedorId,
        String proveedorRazonSocial, Long usuarioId, String numero, LocalDate fechaFactura, BigDecimal subtotal,
        BigDecimal impuestos, BigDecimal total, BigDecimal esperadoSubtotal, BigDecimal esperadoImpuestos,
        BigDecimal esperadoTotal, BigDecimal diferencia, EstadoFactura estado, Long documentoId,
        String motivoAprobacion, Long aprobadoPorUsuarioId, LocalDateTime aprobadoEn, String motivoAnulacion,
        Long anuladoPorUsuarioId, LocalDateTime anuladoEn, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param f factura con orden y proveedor cargados
     * @return DTO
     */
    public static FacturaResponse of(FacturaProveedor f) {
        return new FacturaResponse(f.getId(), f.getOrden().getId(), f.getOrden().getConsecutivo(),
                f.getProveedor().getId(), f.getProveedor().getRazonSocial(), f.getUsuarioId(), f.getNumero(),
                f.getFechaFactura(), f.getSubtotal(), f.getImpuestos(), f.getTotal(), f.getEsperadoSubtotal(),
                f.getEsperadoImpuestos(), f.getEsperadoTotal(), f.getDiferencia(), f.getEstado(), f.getDocumentoId(),
                f.getMotivoAprobacion(), f.getAprobadoPorUsuarioId(), f.getAprobadoEn(), f.getMotivoAnulacion(),
                f.getAnuladoPorUsuarioId(), f.getAnuladoEn(), f.getCreadoEn());
    }
}
