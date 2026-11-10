package com.proyecto.supply_core.security.dto;

/**
 * Respuesta de una sola frase para las rutas públicas que no deben revelar nada más.
 *
 * @param mensaje texto para mostrar al usuario
 */
public record MensajeResponse(String mensaje) {
}
