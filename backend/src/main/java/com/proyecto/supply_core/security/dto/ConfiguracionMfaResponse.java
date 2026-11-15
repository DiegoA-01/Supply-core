package com.proyecto.supply_core.security.dto;

/**
 * Datos para vincular la app de autenticación.
 *
 * @param secretoMfa secreto en base32 (para escribirlo a mano)
 * @param otpauthUri dirección {@code otpauth://} que la app lee desde un código QR
 */
public record ConfiguracionMfaResponse(String secretoMfa, String otpauthUri) {
}
