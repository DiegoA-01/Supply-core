package com.proyecto.supply_core.notificacion.email;

/**
 * Archivo que viaja adjunto en un correo.
 *
 * @param nombre      nombre con extensión (p. ej. {@code OC-000001.pdf})
 * @param contentType tipo MIME
 * @param contenido   bytes del archivo
 */
public record Adjunto(String nombre, String contentType, byte[] contenido) {
}
