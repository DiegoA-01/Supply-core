package com.proyecto.supply_core.usuario.dto;

import java.time.LocalDateTime;
import java.util.Set;

import com.proyecto.supply_core.usuario.entity.Usuario;

/**
 * Usuario tal como lo ve la API. Nunca incluye {@code hash_password}.
 *
 * @param id             id
 * @param nombreCompleto nombre visible
 * @param correo         correo de login
 * @param activo         si puede iniciar sesión
 * @param roles          códigos de rol
 * @param centroCostoId  centro de costo o {@code null}
 * @param proveedorId    proveedor o {@code null}
 * @param ultimoAcceso   último login exitoso
 * @param creadoEn       fecha de creación
 */
public record UsuarioResponse(
        Long id,
        String nombreCompleto,
        String correo,
        boolean activo,
        Set<String> roles,
        Long centroCostoId,
        Long proveedorId,
        LocalDateTime ultimoAcceso,
        LocalDateTime creadoEn) {

    /**
     * Convierte la entidad en DTO.
     *
     * @param u entidad con sus roles cargados
     * @return DTO de respuesta
     */
    public static UsuarioResponse of(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombreCompleto(), u.getCorreo(), u.isActivo(),
                u.codigosRoles(), u.getCentroCostoId(), u.getProveedorId(), u.getUltimoAcceso(),
                u.getCreadoEn());
    }
}
