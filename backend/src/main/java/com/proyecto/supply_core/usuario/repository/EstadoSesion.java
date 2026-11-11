package com.proyecto.supply_core.usuario.repository;

/**
 * Lo mínimo de un usuario para decidir si su JWT sigue valiendo.
 *
 * @param activo        {@code false} si la cuenta se desactivó
 * @param versionSesion versión de sesión actual (sube al cambiar la clave o desactivar)
 */
public record EstadoSesion(boolean activo, int versionSesion) {
}
