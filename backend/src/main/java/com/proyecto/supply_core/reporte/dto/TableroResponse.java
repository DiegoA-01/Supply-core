package com.proyecto.supply_core.reporte.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.proyecto.supply_core.reorden.dto.ReabastecimientoItem;

/**
 * Tablero del dueño de la empresa: cómo va el gasto, cuánto dinero hay en inventario y qué hay que pedir. Todas las
 * cifras las calcula Java; la pantalla solo las dibuja. Las ventas se sumarán cuando exista ese módulo.
 *
 * @param hoy                  día del cálculo
 * @param desde                primer día de los últimos doce meses
 * @param gastoDoceMeses       total de órdenes vigentes de los últimos doce meses (con impuestos)
 * @param pagadoDoceMeses      pagos registrados de esas órdenes
 * @param porPagarDoceMeses    lo que falta por pagar de esas órdenes (gasto − pagado, nunca negativo)
 * @param ordenesDoceMeses     órdenes emitidas en los doce meses
 * @param gastoMesActual       total del mes en curso
 * @param gastoMesAnterior     total del mes anterior completo
 * @param variacionMesPct      cambio del mes actual frente al anterior en % ({@code null} si el anterior fue 0)
 * @param gastoPorMes          doce meses, del más antiguo al actual (los meses sin compras van en 0)
 * @param gastoPorCategoria    categorías con más gasto (máximo {@value #MAXIMO_LISTA})
 * @param gastoPorProveedor    proveedores con más gasto (máximo {@value #MAXIMO_LISTA})
 * @param valorInventario      valor del stock a costo de referencia
 * @param valorSinMovimiento   valor del stock que no tuvo salidas en {@code diasRotacion} días (dinero parado)
 * @param productosActivos     productos activos
 * @param agotados             productos sin stock que sí tuvieron salidas
 * @param bajoMinimo           productos bajo su stock mínimo
 * @param diasRotacion         días usados para medir la rotación
 * @param productosPorClase    cuántos productos hay en cada clase de rotación
 * @param masMovidos           productos con más salidas (máximo {@value #MAXIMO_LISTA})
 * @param menosMovidos         productos con stock y sin salidas, del que más dinero tiene parado al que menos
 *                             (máximo {@value #MAXIMO_LISTA})
 * @param reabastecerUrgentes  productos que se acaban antes de que llegue un pedido hecho hoy
 * @param reabastecerPedirYa   productos que hay que pedir hoy
 * @param reabastecerPronto    productos que hay que pedir en los próximos días hábiles
 * @param porPedir             los más urgentes de pedir (máximo {@value #MAXIMO_LISTA})
 * @param sedeId               sede del tablero ({@code null} = toda la empresa)
 * @param gastoPorSede         compras de los doce meses repartidas por sede (solo en el tablero de toda la empresa)
 */
public record TableroResponse(LocalDate hoy, LocalDate desde, BigDecimal gastoDoceMeses, BigDecimal pagadoDoceMeses,
        BigDecimal porPagarDoceMeses, long ordenesDoceMeses, BigDecimal gastoMesActual, BigDecimal gastoMesAnterior,
        BigDecimal variacionMesPct, List<GastoMes> gastoPorMes, List<GastoItem> gastoPorCategoria,
        List<GastoItem> gastoPorProveedor, BigDecimal valorInventario, BigDecimal valorSinMovimiento,
        int productosActivos, long agotados, long bajoMinimo, int diasRotacion, Map<ClaseRotacion, Long> productosPorClase,
        List<RotacionItem> masMovidos, List<RotacionItem> menosMovidos, long reabastecerUrgentes,
        long reabastecerPedirYa, long reabastecerPronto, List<ReabastecimientoItem> porPedir, Long sedeId,
        List<GastoItem> gastoPorSede) {

    /** Largo máximo de las listas del tablero. */
    public static final int MAXIMO_LISTA = 5;
}
