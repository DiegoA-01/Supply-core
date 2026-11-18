package com.proyecto.supply_core.security.dto;

/**
 * Resultado de un login o de una renovación de sesión.
 * <p>Si el usuario debe confirmar un código de MFA, todavía no hay sesión: {@code token}, {@code usuario} y
 * {@code refreshToken} vienen vacíos y el cliente debe enviar el {@code desafioMfa} junto con el código a
 * {@code /api/auth/mfa}.</p>
 *
 * @param token            JWT de corta vida a enviar en {@code Authorization: Bearer}
 * @param tipo             siempre {@code "Bearer"}
 * @param expiraEnSegundos vigencia del JWT desde ahora
 * @param usuario          datos para armar el menú del frontend
 * @param refreshToken     valor para pedir un JWT nuevo en {@code /api/auth/refresh}; se usa una sola vez
 * @param desafioMfa       solo si falta el segundo factor: se entrega con el código en {@code /api/auth/mfa} (vale 5 min)
 * @param mfaConfigurado   solo con desafío: {@code true} si la persona ya vinculó su app de autenticación
 * @param secretoMfa       solo con desafío y mientras no vincule la app: secreto para hacerlo
 * @param otpauthUri       solo con el secreto: dirección {@code otpauth://} del código QR
 */
public record LoginResponse(
        String token,
        String tipo,
        long expiraEnSegundos,
        UsuarioSesionResponse usuario,
        String refreshToken,
        String desafioMfa,
        Boolean mfaConfigurado,
        String secretoMfa,
        String otpauthUri) {

    /**
     * Sesión completa (sin desafío de MFA).
     *
     * @param token            JWT de corta vida
     * @param tipo             siempre {@code "Bearer"}
     * @param expiraEnSegundos vigencia del JWT
     * @param usuario          datos de sesión
     * @param refreshToken     refresh token de un solo uso
     */
    public LoginResponse(String token, String tipo, long expiraEnSegundos, UsuarioSesionResponse usuario,
            String refreshToken) {
        this(token, tipo, expiraEnSegundos, usuario, refreshToken, null, null, null, null);
    }

    /**
     * Respuesta de un login que todavía necesita el código de MFA.
     *
     * @param desafio     desafío firmado de 5 minutos
     * @param configurado si la app de autenticación ya está vinculada
     * @param secreto     secreto para vincularla ({@code null} si ya está vinculada)
     * @param otpauthUri  dirección del código QR ({@code null} si ya está vinculada)
     * @return respuesta sin sesión
     */
    public static LoginResponse conDesafio(String desafio, boolean configurado, String secreto, String otpauthUri) {
        return new LoginResponse(null, null, 0, null, null, desafio, configurado, secreto, otpauthUri);
    }
}
