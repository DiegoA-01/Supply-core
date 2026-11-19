package com.proyecto.supply_core.reorden.dto;

/**
 * Comparación de sedes: cómo está el abastecimiento de cada una.
 *
 * @param sedeId     sede
 * @param sede       nombre
 * @param productos  productos con stock o consumo en la sede
 * @param urgentes   se acaban antes de que llegue un pedido hecho hoy
 * @param pedirYa    hay que pedirlos hoy
 * @param pronto     hay que pedirlos en los próximos días hábiles
 * @param ok         alcanzan
 * @param sinConsumo sin salidas en el período
 * @param traslados  productos que se pueden cubrir, en todo o en parte, trasladando desde otra sede
 */
public record ResumenSede(Long sedeId, String sede, long productos, long urgentes, long pedirYa, long pronto, long ok,
        long sinConsumo, long traslados) {
}
