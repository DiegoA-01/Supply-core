package com.proyecto.supply_core.security.dto;

/**
 * Resultado de un login o de una renovación de sesión.
 *
 * @param token            JWT de corta vida a enviar en {@code Authorization: Bearer}
 * @param tipo             siempre {@code "Bearer"}
 * @param expiraEnSegundos vigencia del JWT desde ahora
 * @param usuario          datos para armar el menú del frontend
 * @param refreshToken     valor para pedir un JWT nuevo en {@code /api/auth/refresh}; se usa una sola vez
 */
public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioSesionResponse usuario,
        String refreshToken) {
}
