package com.proyecto.supply_core.security.jwt;

/**
 * Contenido de un desafío de MFA de un usuario de empresa.
 *
 * @param usuarioId usuario que acertó la contraseña
 * @param empresaId empresa en la que debe continuar el login
 */
public record DesafioMfaEmpresa(Long usuarioId, Long empresaId) {
}
