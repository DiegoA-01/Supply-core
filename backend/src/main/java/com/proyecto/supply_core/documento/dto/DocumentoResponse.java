package com.proyecto.supply_core.documento.dto;

import java.time.LocalDateTime;

import com.proyecto.supply_core.documento.entity.Documento;

/**
 * Metadata de un documento tal como la ve la API. Nunca incluye la ruta interna del almacenamiento.
 *
 * @param id                 id
 * @param nombreArchivo      nombre original saneado
 * @param tipoMime           tipo MIME detectado
 * @param tamanoBytes        tamaño
 * @param entidad            tipo de registro al que pertenece
 * @param entidadId          id de ese registro
 * @param subidoPorUsuarioId quién lo subió
 * @param creadoEn           cuándo
 */
public record DocumentoResponse(Long id, String nombreArchivo, String tipoMime, long tamanoBytes, String entidad,
        Long entidadId, Long subidoPorUsuarioId, LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param d documento
     * @return DTO
     */
    public static DocumentoResponse of(Documento d) {
        return new DocumentoResponse(d.getId(), d.getNombreArchivo(), d.getTipoMime(), d.getTamanoBytes(),
                d.getEntidadTipo(), d.getEntidadId(), d.getSubidoPorUsuarioId(), d.getCreadoEn());
    }
}
