package com.proyecto.supply_core.cartera.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cartera de la empresa: un renglón por cliente con crédito o con saldo, y los totales por edad.
 *
 * @param clientes clientes
 * @param saldo    saldo total por cobrar
 * @param alDia    total al día
 * @param d1a30    total vencido 1 a 30 días
 * @param d31a60   31 a 60 días
 * @param d61a90   61 a 90 días
 * @param mas90    más de 90 días
 * @param enMora   cuántos clientes tienen cuentas vencidas
 */
public record ResumenCartera(List<ClienteCartera> clientes, BigDecimal saldo, BigDecimal alDia, BigDecimal d1a30,
        BigDecimal d31a60, BigDecimal d61a90, BigDecimal mas90, int enMora) {
}
