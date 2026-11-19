package com.proyecto.supply_core.venta.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Precios y margen de una lista.
 *
 * @param lista               lista consultada
 * @param productos           productos vendibles activos
 * @param conPrecio           cuántos tienen precio en la lista
 * @param sinPrecio           cuántos no tienen
 * @param bajoCosto           cuántos se venderían por debajo del costo
 * @param margenPromedioPct   promedio simple del margen % de los que tienen precio y costo
 * @param margenPonderadoPct  margen global ponderado por el inventario: Σ(margen × stock) ÷ Σ(base × stock)
 * @param margenInventario    ganancia teórica si se vendiera todo el inventario a estos precios
 * @param puedeEditar         {@code true} si el usuario de la sesión puede fijar precios
 * @param filas               un producto por fila, por nombre
 */
public record PreciosResponse(ListaPrecioResponse lista, int productos, long conPrecio, long sinPrecio, long bajoCosto,
        BigDecimal margenPromedioPct, BigDecimal margenPonderadoPct, BigDecimal margenInventario, boolean puedeEditar,
        List<PrecioFila> filas) {
}
