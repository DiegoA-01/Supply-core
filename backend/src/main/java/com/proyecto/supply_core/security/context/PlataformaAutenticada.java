package com.proyecto.supply_core.security.context;

/**
 * Usuario de la plataforma que viaja dentro de su JWT y queda disponible durante la petición a
 * {@code /api/plataforma/**}. No tiene empresa ni roles de empresa.
 *
 * @param id     id en {@code usuario_plataforma}
 * @param correo correo de login
 * @param nombre nombre completo
 */
public record PlataformaAutenticada(Long id, String correo, String nombre) {
}
