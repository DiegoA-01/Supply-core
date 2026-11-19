package com.proyecto.supply_core.venta.dto;

import java.util.List;

import com.proyecto.supply_core.inventario.dto.CargaMasivaResponse.ErrorFila;

/**
 * Resultado de una carga de precios desde Excel. Es de todo o nada: con algún error no se guarda ningún precio y
 * {@code aplicado} es {@code false}.
 *
 * @param soloValidar {@code true} si se pidió solo revisar el archivo
 * @param aplicado    {@code true} si se guardaron los precios
 * @param filas       filas con datos leídas
 * @param precios     precios que se fijan (o se fijarían)
 * @param errores     problemas por fila (vacío si todo está bien)
 */
public record CargaPreciosResponse(boolean soloValidar, boolean aplicado, int filas, int precios,
        List<ErrorFila> errores) {
}
