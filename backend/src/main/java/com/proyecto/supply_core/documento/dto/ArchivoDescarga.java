package com.proyecto.supply_core.documento.dto;

import org.springframework.core.io.Resource;

/**
 * Archivo listo para enviar al cliente.
 *
 * @param contenido     recurso a transmitir
 * @param nombreArchivo nombre para el header Content-Disposition
 * @param tipoMime      tipo MIME canónico
 * @param tamanoBytes   tamaño (Content-Length)
 */
public record ArchivoDescarga(Resource contenido, String nombreArchivo, String tipoMime, long tamanoBytes) {
}
