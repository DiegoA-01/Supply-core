package com.proyecto.supply_core.pago.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.pago.entity.Pago;
import com.proyecto.supply_core.pago.enums.EstadoPago;
import com.proyecto.supply_core.pago.enums.MetodoPago;
import com.proyecto.supply_core.pago.enums.TipoPago;

/**
 * Pago de una orden.
 *
 * @param id                     id
 * @param ordenId                versión de la orden a la que se pagó
 * @param ordenConsecutivo       OC-000001
 * @param ordenVersion           versión
 * @param proveedorRazonSocial   proveedor
 * @param usuarioId              quién lo registró
 * @param metodo                 medio de pago
 * @param tipo                   ANTICIPO o PAGO_FINAL
 * @param valor                  valor
 * @param fechaPago              fecha del pago
 * @param referencia             referencia
 * @param estado                 REGISTRADO o ANULADO
 * @param evidenciaPendiente     {@code true} si falta el comprobante
 * @param comprobanteDocumentoId documento del comprobante
 * @param motivoAnulacion        motivo, si se anuló
 * @param anuladoPorUsuarioId    quién lo anuló
 * @param anuladoEn              cuándo se anuló
 * @param creadoEn               cuándo se registró
 */
public record PagoResponse(Long id, Long ordenId, String ordenConsecutivo, int ordenVersion,
        String proveedorRazonSocial, Long usuarioId, MetodoPago metodo, TipoPago tipo, BigDecimal valor, LocalDate fechaPago,
        String referencia, EstadoPago estado, boolean evidenciaPendiente, Long comprobanteDocumentoId,
        String motivoAnulacion, Long anuladoPorUsuarioId, LocalDateTime anuladoEn, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param p pago con orden y proveedor
     * @return DTO
     */
    public static PagoResponse of(Pago p) {
        return new PagoResponse(p.getId(), p.getOrden().getId(), p.getOrden().getConsecutivo(),
                p.getOrden().getVersion(), p.getOrden().getProveedor().getRazonSocial(), p.getUsuarioId(),
                p.getMetodo(), p.getTipo(), p.getValor(), p.getFechaPago(), p.getReferencia(), p.getEstado(),
                p.isEvidenciaPendiente(), p.getComprobanteDocumentoId(), p.getMotivoAnulacion(),
                p.getAnuladoPorUsuarioId(), p.getAnuladoEn(), p.getCreadoEn());
    }
}
