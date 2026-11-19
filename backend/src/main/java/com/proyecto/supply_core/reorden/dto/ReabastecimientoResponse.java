package com.proyecto.supply_core.reorden.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Plan de reabastecimiento de la empresa: qué pedir, cuándo y cuánto, según el consumo real y el plazo de entrega.
 *
 * @param hoy                 día del cálculo
 * @param diasConsumo         días de historia usados para el consumo diario
 * @param plazoEntregaDefecto días hábiles de entrega por defecto de la empresa
 * @param seguridadDefecto    días hábiles de colchón por defecto de la empresa
 * @param coberturaDefecto    días de calendario que debe cubrir un pedido al llegar
 * @param urgentes            productos que se acaban antes de que llegue un pedido hecho hoy
 * @param pedirYa             productos en el punto de pedido o bajo el mínimo
 * @param pronto              productos que hay que pedir en los próximos días hábiles
 * @param ok                  productos que alcanzan holgados
 * @param sinConsumo          productos sin salidas en el período
 * @param items               productos, de más a menos urgente
 */
public record ReabastecimientoResponse(LocalDate hoy, int diasConsumo, int plazoEntregaDefecto, int seguridadDefecto,
        int coberturaDefecto, long urgentes, long pedirYa, long pronto, long ok, long sinConsumo,
        List<ReabastecimientoItem> items) {
}
