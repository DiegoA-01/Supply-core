package com.proyecto.supply_core.security.context;

import java.util.Set;

/**
 * Datos del usuario que viajan dentro del JWT y quedan disponibles durante la petición.
 *
 * @param id          id del usuario
 * @param correo      correo (login)
 * @param nombre      nombre completo
 * @param roles       códigos de rol (ver {@link Roles})
 * @param proveedorId id del proveedor si es usuario del portal; {@code null} si es interno
 */
public record UsuarioAutenticado(
        Long id,
        String correo,
        String nombre,
        Set<String> roles,
        Long proveedorId) {

    /**
     * Copia defensiva para que los roles no se puedan modificar durante la petición.
     *
     * @param id          id del usuario
     * @param correo      correo
     * @param nombre      nombre completo
     * @param roles       códigos de rol
     * @param proveedorId id del proveedor o {@code null}
     */
    public UsuarioAutenticado {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /**
     * Indica si el usuario tiene al menos uno de los roles.
     *
     * @param codigos códigos de rol aceptados
     * @return {@code true} si tiene alguno
     */
    public boolean tieneAlgunRol(String... codigos) {
        for (String codigo : codigos) {
            if (roles.contains(codigo)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indica si es un usuario del portal proveedor.
     *
     * @return {@code true} si tiene proveedor asociado
     */
    public boolean esProveedor() {
        return proveedorId != null;
    }
}
