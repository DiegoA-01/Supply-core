package com.proyecto.supply_core.catalogoproveedor.dto;

import java.util.List;

/**
 * Resultado de subir un catálogo. Si hay errores no se guarda nada (todo o nada).
 *
 * @param aplicado     {@code true} si se guardó
 * @param nuevos       artículos nuevos
 * @param actualizados artículos que ya estaban y cambiaron de precio o descripción
 * @param retirados    artículos que ya no vienen en el archivo (quedan inactivos)
 * @param errores      filas con problemas
 */
public record CargaCatalogoResponse(boolean aplicado, int nuevos, int actualizados, int retirados,
        List<ErrorFila> errores) {

    /**
     * Problema de una fila del Excel.
     *
     * @param fila    número de fila
     * @param mensaje qué está mal
     */
    public record ErrorFila(int fila, String mensaje) {
    }
}
