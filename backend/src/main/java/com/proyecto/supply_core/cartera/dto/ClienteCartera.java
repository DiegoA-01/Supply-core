package com.proyecto.supply_core.cartera.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cartera de un cliente.
 *
 * @param clienteId         cliente
 * @param nombre            nombre
 * @param documento         tipo y número de documento
 * @param cupo              cupo de crédito
 * @param plazoDias         plazo de pago en días
 * @param saldo             saldo por cobrar
 * @param disponible        cupo menos saldo (nunca negativo)
 * @param saldoFavor        saldo a favor
 * @param alDia             saldo que aún no vence
 * @param vencido           saldo ya vencido (saldo menos lo que está al día)
 * @param d1a30             saldo vencido 1 a 30 días
 * @param d31a60            31 a 60 días
 * @param d61a90            61 a 90 días
 * @param mas90             más de 90 días
 * @param enMora            {@code true} si tiene alguna cuenta vencida
 * @param bloqueado         {@code true} si está en mora y no tiene excepción vigente (no compra a crédito)
 * @param excepcionHasta    hasta cuándo puede comprar a crédito aunque esté en mora
 * @param excepcionMotivo   motivo de la excepción
 * @param excepcionPor      quién dio la excepción
 */
public record ClienteCartera(Long clienteId, String nombre, String documento, BigDecimal cupo, int plazoDias,
        BigDecimal saldo, BigDecimal disponible, BigDecimal saldoFavor, BigDecimal alDia, BigDecimal vencido, BigDecimal d1a30,
        BigDecimal d31a60, BigDecimal d61a90, BigDecimal mas90, boolean enMora, boolean bloqueado,
        LocalDate excepcionHasta, String excepcionMotivo, String excepcionPor) {
}
