package com.proyecto.supply_core.exportacion.enums;

/**
 * Qué se hace con el archivo exportado; cada destino se audita con su propia acción.
 */
public enum DestinoExportacion {

    /** Se devuelve como descarga en la respuesta. */
    DESCARGA,
    /** Se envía como adjunto al correo del usuario de la sesión. */
    CORREO
}
