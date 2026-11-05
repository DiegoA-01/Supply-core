package com.proyecto.supply_core.plataforma.dto;

/**
 * Sesión de la plataforma tras acertar la contraseña y el código de MFA.
 *
 * @param token          JWT para el header {@code Authorization: Bearer}; solo sirve en {@code /api/plataforma/**}
 * @param tipo           siempre {@code Bearer}
 * @param expiraEnSegundos segundos de vigencia
 * @param usuarioId      id del usuario de la plataforma
 * @param correo         correo
 * @param nombre         nombre
 */
public record SesionPlataformaResponse(String token, String tipo, long expiraEnSegundos, Long usuarioId, String correo,
        String nombre) {
}
