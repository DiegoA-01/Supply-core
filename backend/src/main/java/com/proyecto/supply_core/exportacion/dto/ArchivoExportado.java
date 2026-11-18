package com.proyecto.supply_core.exportacion.dto;

import com.proyecto.supply_core.exportacion.enums.FormatoExportacion;

/**
 * Archivo generado por una exportación, listo para descargar o adjuntar.
 *
 * @param nombre    nombre con extensión
 * @param formato   formato del archivo
 * @param contenido bytes del archivo
 * @param filas     registros incluidos (líneas en el caso de la orden)
 */
public record ArchivoExportado(String nombre, FormatoExportacion formato, byte[] contenido, int filas) {
}
