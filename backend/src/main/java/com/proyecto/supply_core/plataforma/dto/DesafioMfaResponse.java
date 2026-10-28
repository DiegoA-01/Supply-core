package com.proyecto.supply_core.plataforma.dto;

/**
 * Respuesta del primer paso del login: todavía no hay sesión, falta el código de MFA.
 *
 * @param desafio        se entrega junto con el código en {@code /api/plataforma/auth/mfa} (vale 5 minutos)
 * @param mfaConfigurado {@code true} si la persona ya tiene su app de autenticación vinculada
 * @param secretoMfa     secreto para vincular la app; solo viene mientras no la haya confirmado ({@code null} después)
 * @param otpauthUri     dirección {@code otpauth://} del código QR; solo viene con el secreto
 */
public record DesafioMfaResponse(String desafio, boolean mfaConfigurado, String secretoMfa, String otpauthUri) {
}
