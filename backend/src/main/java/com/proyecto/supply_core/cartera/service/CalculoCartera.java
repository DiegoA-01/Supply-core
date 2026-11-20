package com.proyecto.supply_core.cartera.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Edades de la cartera (spec 007), sin base de datos para poder probarlas solas. */
public final class CalculoCartera {

    /** No se instancia: solo métodos estáticos. */
    private CalculoCartera() {
    }

    /**
     * Una cuenta para clasificar.
     *
     * @param saldo   saldo por cobrar
     * @param venceEn vencimiento
     */
    public record Cuenta(BigDecimal saldo, LocalDate venceEn) {
    }

    /**
     * Saldo por tramo de mora. La suma de los tramos es siempre el total.
     *
     * @param alDia   aún no vence
     * @param d1a30   1 a 30 días vencida
     * @param d31a60  31 a 60 días
     * @param d61a90  61 a 90 días
     * @param mas90   más de 90 días
     * @param total   saldo total
     */
    public record Edades(BigDecimal alDia, BigDecimal d1a30, BigDecimal d31a60, BigDecimal d61a90, BigDecimal mas90,
            BigDecimal total) {

        /**
         * @return lo vencido (todo menos lo que está al día)
         */
        public BigDecimal vencido() {
            return total.subtract(alDia);
        }
    }

    /**
     * @param venceEn vencimiento
     * @param hoy     fecha de hoy
     * @return días vencida (0 si aún no vence)
     */
    public static long diasVencida(LocalDate venceEn, LocalDate hoy) {
        return Math.max(0, ChronoUnit.DAYS.between(venceEn, hoy));
    }

    /**
     * @param cuentas cuentas con saldo
     * @param hoy     fecha de hoy
     * @return saldo por tramo de mora
     */
    public static Edades edades(List<Cuenta> cuentas, LocalDate hoy) {
        BigDecimal[] t = { BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2) };
        for (Cuenta c : cuentas) {
            long dias = diasVencida(c.venceEn(), hoy);
            int i = dias == 0 ? 0 : dias <= 30 ? 1 : dias <= 60 ? 2 : dias <= 90 ? 3 : 4;
            t[i] = t[i].add(c.saldo());
        }
        BigDecimal total = t[0].add(t[1]).add(t[2]).add(t[3]).add(t[4]);
        return new Edades(t[0], t[1], t[2], t[3], t[4], total);
    }
}
