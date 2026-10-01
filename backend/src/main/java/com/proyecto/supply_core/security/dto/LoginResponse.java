package com.proyecto.supply_core.security.dto;

/**
 * Resultado de un login exitoso.
 *
 * @param token            JWT a enviar en {@code Authorization: Bearer}
 * @param tipo             siempre {@code "Bearer"}
 * @param expiraEnSegundos vigencia del token desde ahora
 * @param usuario          datos para armar el menú del frontend
 */
public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioSesionResponse usuario) {
}
