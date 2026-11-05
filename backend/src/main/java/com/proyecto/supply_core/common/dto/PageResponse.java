package com.proyecto.supply_core.common.dto;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * Respuesta paginada estable para el frontend (no expone la estructura interna de Spring {@link Page}).
 *
 * @param contenido      elementos de la página actual
 * @param pagina         número de página (empieza en 0)
 * @param tamano         tamaño de página solicitado
 * @param totalElementos total de registros que cumplen el filtro
 * @param totalPaginas   total de páginas
 * @param <T>            tipo de cada elemento (un DTO de respuesta)
 */
public record PageResponse<T>(
        List<T> contenido,
        int pagina,
        int tamano,
        long totalElementos,
        int totalPaginas) {

    /**
     * Convierte una página de entidades en una página de DTOs.
     *
     * @param page   página devuelta por el repositorio
     * @param mapper función entidad → DTO
     * @param <E>    tipo de la entidad
     * @param <T>    tipo del DTO
     * @return página lista para serializar
     */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
