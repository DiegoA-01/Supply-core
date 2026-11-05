package com.proyecto.supply_core.security.dto;

import java.util.Set;

import com.proyecto.supply_core.security.context.UsuarioAutenticado;

/**
 * Datos del usuario logueado que el frontend necesita para armar el menú.
 *
 * @param id          id del usuario
 * @param nombre      nombre completo
 * @param correo      correo
 * @param roles       códigos de rol
 * @param proveedorId id del proveedor o {@code null} si es interno
 */
public record UsuarioSesionResponse(Long id, String nombre, String correo, Set<String> roles, Long proveedorId) {

    /**
     * Construye la respuesta a partir de la sesión.
     *
     * @param u usuario autenticado
     * @return DTO de sesión
     */
    public static UsuarioSesionResponse of(UsuarioAutenticado u) {
        return new UsuarioSesionResponse(u.id(), u.nombre(), u.correo(), u.roles(), u.proveedorId());
    }
}
