package com.proyecto.supply_core.auditoria.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.auditoria.entity.Auditoria;
import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;

/**
 * Registro de auditoría tal como lo ve el auditor.
 *
 * @param id            id del registro
 * @param usuarioId     quién ejecutó la acción ({@code null} si fue anónima)
 * @param usuarioNombre nombre de ese usuario ({@code null} si fue anónima)
 * @param usuarioCorreo correo de ese usuario ({@code null} si fue anónima)
 * @param entidad       tipo de entidad afectada
 * @param entidadId     id del registro afectado
 * @param accion        acción realizada
 * @param resultado     éxito o fallo
 * @param ip            IP de origen
 * @param detalle       contexto de la acción
 * @param creadoEn      cuándo ocurrió
 */
public record AuditoriaResponse(
        Long id,
        Long usuarioId,
        String usuarioNombre,
        String usuarioCorreo,
        String entidad,
        Long entidadId,
        String accion,
        ResultadoAuditoria resultado,
        String ip,
        String detalle,
        LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO agregando los datos del usuario.
     *
     * @param a       registro de auditoría
     * @param nombre  nombre del usuario o {@code null}
     * @param correo  correo del usuario o {@code null}
     * @return DTO de respuesta
     */
    public static AuditoriaResponse of(Auditoria a, String nombre, String correo) {
        return new AuditoriaResponse(a.getId(), a.getUsuarioId(), nombre, correo, a.getEntidad(),
                a.getEntidadId(), a.getAccion(), a.getResultado(), a.getIp(), a.getDetalle(), a.getCreadoEn());
    }
}
