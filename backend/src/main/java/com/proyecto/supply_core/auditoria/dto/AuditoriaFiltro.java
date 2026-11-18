package com.proyecto.supply_core.auditoria.dto;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.proyecto.supply_core.auditoria.enums.AccionAuditoria;
import com.proyecto.supply_core.auditoria.enums.EntidadAuditada;
import com.proyecto.supply_core.auditoria.enums.ResultadoAuditoria;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;

/**
 * Filtros opcionales de la consulta de auditoría (UC-16), recibidos como query params.
 * Todos son opcionales; los presentes se combinan con AND.
 *
 * @param usuarioId quién ejecutó la acción
 * @param entidad   tipo de entidad afectada
 * @param entidadId id del registro afectado
 * @param accion    acción realizada
 * @param resultado éxito o fallo
 * @param desde     fecha/hora inicial inclusive (ISO, p. ej. {@code 2026-10-01T00:00:00})
 * @param hasta     fecha/hora final inclusive (ISO)
 */
public record AuditoriaFiltro(
        @Positive(message = "debe ser un id válido") Long usuarioId,
        EntidadAuditada entidad,
        @Positive(message = "debe ser un id válido") Long entidadId,
        AccionAuditoria accion,
        ResultadoAuditoria resultado,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {

    /**
     * Regla entre campos: el rango de fechas no puede estar invertido.
     *
     * @return {@code true} si falta alguna fecha o {@code desde <= hasta}
     */
    @AssertTrue(message = "la fecha 'desde' no puede ser posterior a 'hasta'")
    public boolean isRangoValido() {
        return desde == null || hasta == null || !desde.isAfter(hasta);
    }
}
