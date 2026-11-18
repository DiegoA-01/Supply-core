package com.proyecto.supply_core.compra.solicitud.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.proyecto.supply_core.compra.solicitud.entity.AprobacionSolicitud;
import com.proyecto.supply_core.compra.solicitud.entity.SolicitudCompra;
import com.proyecto.supply_core.compra.solicitud.enums.EstadoSolicitud;

/**
 * Detalle de una solicitud con sus líneas y el historial de decisiones.
 *
 * @param id                   id
 * @param consecutivo          SOL-000001
 * @param estado               estado
 * @param centroCostoId        centro de costo
 * @param usuarioSolicitanteId solicitante
 * @param motivo               justificación
 * @param fechaRequerida       fecha en que se necesita
 * @param montoEstimado        monto estimado (al enviar)
 * @param montoIncompleto      si faltó el costo de algún producto
 * @param politicaAprobacionId política asignada al enviar
 * @param excedePresupuesto    si al enviarla excedía el presupuesto del centro de costo
 * @param justificacionExcepcion justificación de la excepción ({@code null} si se envió sin ella)
 * @param escaladaEn           cuándo venció el SLA y pasó a la política de mayor tramo ({@code null} si no)
 * @param enviadoEn            fecha de envío
 * @param creadoEn             fecha de creación
 * @param lineas               líneas
 * @param aprobaciones         decisiones, de la más antigua a la más reciente
 * @param apruebaDueno         si la decide el administrador de la empresa porque quien la pidió aprueba con su rol
 */
public record SolicitudResponse(Long id, String consecutivo, EstadoSolicitud estado, Long centroCostoId,
        Long usuarioSolicitanteId, String motivo, LocalDate fechaRequerida, BigDecimal montoEstimado,
        boolean montoIncompleto, Long politicaAprobacionId, boolean excedePresupuesto, String justificacionExcepcion,
        LocalDateTime escaladaEn, LocalDateTime enviadoEn, LocalDateTime creadoEn, List<LineaSolicitudResponse> lineas,
        List<AprobacionResponse> aprobaciones, boolean apruebaDueno) {

    /**
     * Convierte la entidad (líneas con producto y unidad cargados).
     *
     * @param s            solicitud
     * @param aprobaciones historial de decisiones
     * @return DTO
     */
    public static SolicitudResponse of(SolicitudCompra s, List<AprobacionSolicitud> aprobaciones) {
        return new SolicitudResponse(s.getId(), s.getConsecutivo(), s.getEstado(), s.getCentroCostoId(),
                s.getUsuarioSolicitanteId(), s.getMotivo(), s.getFechaRequerida(), s.getMontoEstimado(),
                s.isMontoIncompleto(), s.getPoliticaAprobacionId(), s.isExcedePresupuesto(),
                s.getJustificacionExcepcion(), s.getEscaladaEn(), s.getEnviadoEn(), s.getCreadoEn(),
                s.getLineas().stream().map(LineaSolicitudResponse::of).toList(),
                aprobaciones.stream().map(AprobacionResponse::of).toList(), s.isApruebaDueno());
    }
}
