package com.proyecto.supply_core.proveedor.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.proyecto.supply_core.catalogo.dto.CategoriaResponse;
import com.proyecto.supply_core.proveedor.entity.Proveedor;
import com.proyecto.supply_core.proveedor.enums.EstadoProveedor;

/**
 * Proveedor tal como lo ve la API.
 *
 * @param id             id
 * @param razonSocial    nombre legal
 * @param nit            NIT
 * @param correoContacto correo de contacto
 * @param telefono       teléfono
 * @param direccion      dirección
 * @param scoreActual    puntaje acumulado o {@code null}
 * @param estado         estado actual
 * @param motivoEstado   motivo del rechazo o la suspensión
 * @param categorias     categorías que puede cotizar
 * @param creadoEn       fecha de registro
 * @param revisadoEn     fecha de la última revisión
 */
public record ProveedorResponse(
        Long id,
        String razonSocial,
        String nit,
        String correoContacto,
        String telefono,
        String direccion,
        BigDecimal scoreActual,
        EstadoProveedor estado,
        String motivoEstado,
        List<CategoriaResponse> categorias,
        LocalDateTime creadoEn,
        LocalDateTime revisadoEn) {

    /**
     * Convierte la entidad en DTO (categorías ordenadas por nombre).
     *
     * @param p proveedor con categorías cargadas
     * @return DTO
     */
    public static ProveedorResponse of(Proveedor p) {
        List<CategoriaResponse> categorias = p.getCategorias().stream().map(CategoriaResponse::of)
                .sorted(Comparator.comparing(CategoriaResponse::nombre)).toList();
        return new ProveedorResponse(p.getId(), p.getRazonSocial(), p.getNit(), p.getCorreoContacto(),
                p.getTelefono(), p.getDireccion(), p.getScoreActual(), p.getEstado(), p.getMotivoEstado(),
                categorias, p.getCreadoEn(), p.getRevisadoEn());
    }
}
