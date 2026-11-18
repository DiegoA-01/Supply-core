package com.proyecto.supply_core.compra.rfq.dto;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.proyecto.supply_core.compra.rfq.entity.Rfq;
import com.proyecto.supply_core.compra.rfq.enums.EstadoRfq;

/**
 * Detalle interno de una RFQ (comprador, aprobador, auditor).
 *
 * @param id                   id
 * @param consecutivo          RFQ-000001
 * @param estado               estado
 * @param solicitudId          solicitud de origen
 * @param solicitudConsecutivo consecutivo de la solicitud
 * @param usuarioId            comprador que la creó
 * @param fechaLimite          fecha límite para cotizar
 * @param condiciones          condiciones comerciales
 * @param abiertaEn            fecha de apertura
 * @param cerradaEn            fecha de cierre
 * @param creadoEn             fecha de creación
 * @param lineas               productos a cotizar
 * @param invitados            proveedores invitados
 * @param alertas              avisos para el comprador (p. ej. categorías sin proveedores habilitados)
 */
public record RfqResponse(Long id, String consecutivo, EstadoRfq estado, Long solicitudId, String solicitudConsecutivo,
        Long usuarioId, LocalDateTime fechaLimite, String condiciones, LocalDateTime abiertaEn,
        LocalDateTime cerradaEn, LocalDateTime creadoEn, List<RfqLineaResponse> lineas,
        List<InvitadoResponse> invitados, List<String> alertas) {

    /**
     * Convierte la entidad (detalle cargado).
     *
     * @param r       RFQ
     * @param alertas avisos calculados por el Service
     * @return DTO
     */
    public static RfqResponse of(Rfq r, List<String> alertas) {
        return new RfqResponse(r.getId(), r.getConsecutivo(), r.getEstado(), r.getSolicitud().getId(),
                r.getSolicitud().getConsecutivo(), r.getUsuarioId(), r.getFechaLimite(), r.getCondiciones(),
                r.getAbiertaEn(), r.getCerradaEn(), r.getCreadoEn(),
                r.getLineas().stream().map(RfqLineaResponse::of).toList(),
                r.getInvitados().stream()
                        .map(i -> new InvitadoResponse(i.getProveedor().getId(), i.getProveedor().getRazonSocial(),
                                i.getInvitadoEn()))
                        .sorted(Comparator.comparing(InvitadoResponse::razonSocial)).toList(),
                alertas);
    }

    /**
     * Proveedor invitado.
     *
     * @param proveedorId proveedor
     * @param razonSocial razón social
     * @param invitadoEn  fecha de invitación
     */
    public record InvitadoResponse(Long proveedorId, String razonSocial, LocalDateTime invitadoEn) {
    }
}
