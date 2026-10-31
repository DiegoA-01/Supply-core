package com.proyecto.supply_core.exportacion.dto;

/**
 * Resultado de enviar por correo un archivo exportado.
 *
 * @param para    correo al que se envió (siempre el del usuario de la sesión)
 * @param archivo nombre del archivo adjunto
 * @param bytes   tamaño del adjunto
 */
public record CorreoExportacionResponse(String para, String archivo, long bytes) {
}
