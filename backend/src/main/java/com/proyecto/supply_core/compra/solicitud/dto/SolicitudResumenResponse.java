package com.proyecto.supply_core.compra.solicitud.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;

/**
 * Solicitud para listados (sin líneas ni historial).
 *
 * @param id                   id
 * @param consecutivo          SOL-000001
 * @param estado               estado
 * @param centroCostoId        centro de costo
 * @param usuarioSolicitanteId solicitante
 * @param fechaRequerida       fecha en que se necesita
 * @param montoEstimado        monto estimado (al enviar)
 * @param montoIncompleto      si faltó el costo de algún producto
 * @param enviadoEn            fecha de envío
 * @param creadoEn             fecha de creación
 */
public record SolicitudResumenResponse(Long id, String consecutivo, EstadoSolicitud estado, Long centroCostoId,
        Long usuarioSolicitanteId, LocalDate fechaRequerida, BigDecimal montoEstimado, boolean montoIncompleto,
        LocalDateTime enviadoEn, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param s solicitud
     * @return DTO
     */
    public static SolicitudResumenResponse of(SolicitudCompra s) {
        return new SolicitudResumenResponse(s.getId(), s.getConsecutivo(), s.getEstado(), s.getCentroCostoId(),
                s.getUsuarioSolicitanteId(), s.getFechaRequerida(), s.getMontoEstimado(), s.isMontoIncompleto(),
                s.getEnviadoEn(), s.getCreadoEn());
    }
}
