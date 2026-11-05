package com.proyecto.supply_core.inventario.dto;

import java.util.List;

/**
 * Resultado de una carga masiva de productos desde Excel. La carga es de todo o nada: si hay algún error no se
 * guarda nada y {@code aplicado} es {@code false}; los números dicen qué pasaría o qué pasó.
 *
 * @param soloValidar       {@code true} si se pidió solo revisar el archivo, sin guardar
 * @param aplicado          {@code true} si se guardaron los cambios
 * @param filas             filas con datos que se leyeron (sin contar el encabezado ni las vacías)
 * @param creados           productos nuevos
 * @param actualizados      productos que ya existían y se actualizaron
 * @param categoriasCreadas categorías nuevas que el archivo nombra y no existían
 * @param errores           problemas por fila (vacío si todo está bien)
 */
public record CargaMasivaResponse(boolean soloValidar, boolean aplicado, int filas, int creados, int actualizados,
        int categoriasCreadas, List<ErrorFila> errores) {

    /**
     * Problema de una fila del archivo.
     *
     * @param fila    número de fila en el Excel (el encabezado es la 1)
     * @param mensaje qué está mal y cómo corregirlo
     */
    public record ErrorFila(int fila, String mensaje) {
    }
}
