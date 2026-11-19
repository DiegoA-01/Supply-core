package com.proyecto.supply_core.caja.dto;

import java.time.LocalDateTime;

/**
 * Permiso de un solo uso.
 *
 * @param token      se envía en la acción del cajero; vale una vez
 * @param venceEn    hasta cuándo vale (2 minutos)
 * @param supervisor nombre de quien autorizó
 */
public record AutorizacionResponse(String token, LocalDateTime venceEn, String supervisor) {
}
