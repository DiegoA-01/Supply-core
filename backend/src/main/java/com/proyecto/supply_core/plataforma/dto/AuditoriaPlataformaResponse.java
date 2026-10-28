package com.proyecto.supply_core.plataforma.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.plataforma.entity.AuditoriaPlataforma;
import com.proyecto.supply_core.plataforma.enums.AccionPlataforma;

/**
 * Registro de la auditoría global.
 *
 * @param id                  id
 * @param usuarioPlataformaId quién actuó
 * @param accion              qué hizo
 * @param empresaId           empresa afectada
 * @param resultado           EXITO o FALLO
 * @param ip                  dirección de origen
 * @param detalle             datos relevantes
 * @param creadoEn            fecha
 */
public record AuditoriaPlataformaResponse(Long id, Long usuarioPlataformaId, AccionPlataforma accion, Long empresaId,
        String resultado, String ip, String detalle, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad.
     *
     * @param a registro
     * @return DTO
     */
    public static AuditoriaPlataformaResponse of(AuditoriaPlataforma a) {
        return new AuditoriaPlataformaResponse(a.getId(), a.getUsuarioPlataformaId(), a.getAccion(), a.getEmpresaId(),
                a.getResultado(), a.getIp(), a.getDetalle(), a.getCreadoEn());
    }
}
