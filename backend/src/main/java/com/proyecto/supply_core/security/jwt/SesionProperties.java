package com.proyecto.supply_core.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Positive;

/**
 * Configuración tipada de la sesión ({@code app.sesion.*}). Se valida al arrancar.
 *
 * @param refreshDias        vigencia del refresh token en días
 * @param maxIntentos        intentos fallidos seguidos que bloquean la cuenta
 * @param bloqueoMinutos     minutos que dura el bloqueo
 * @param limitePorMinuto    peticiones por minuto y por IP a las rutas públicas sensibles
 */
@Validated
@ConfigurationProperties(prefix = "app.sesion")
public record SesionProperties(
        @Positive(message = "debe ser mayor que cero") long refreshDias,
        @Positive(message = "debe ser mayor que cero") int maxIntentos,
        @Positive(message = "debe ser mayor que cero") long bloqueoMinutos,
        @Positive(message = "debe ser mayor que cero") int limitePorMinuto) {

    /** Valores por defecto cuando no se configura nada (7 días, 5 intentos, 15 minutos, 20 por minuto). */
    public SesionProperties {
        refreshDias = refreshDias <= 0 ? 7 : refreshDias;
        maxIntentos = maxIntentos <= 0 ? 5 : maxIntentos;
        bloqueoMinutos = bloqueoMinutos <= 0 ? 15 : bloqueoMinutos;
        limitePorMinuto = limitePorMinuto <= 0 ? 20 : limitePorMinuto;
    }
}
