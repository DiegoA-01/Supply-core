package com.proyecto.supply_core.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configuración tipada del JWT ({@code app.jwt.*}). Se valida al arrancar.
 *
 * @param secret            {@code JWT_SECRET}, mínimo 32 caracteres. Vacío = secreto aleatorio
 *                          por arranque (solo desarrollo: los tokens dejan de servir al reiniciar)
 * @param expiracionMinutos vigencia del token en minutos (mayor que cero)
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, @Positive(message = "debe ser mayor que cero") long expiracionMinutos) {
}
