package com.proyecto.supply_core.caja.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import com.proyecto.supply_core.caja.entity.MovimientoCaja;
import com.proyecto.supply_core.caja.enums.MedioPago;

/**
 * Cuentas de la caja, en Java y sin base de datos (spec 003). El esperado de cada medio es la suma de sus movimientos:
 * {@code base + ventas en efectivo + abonos − devoluciones en efectivo − retiros − gastos} en el efectivo, y lo
 * cobrado en cada otro medio. La diferencia es contado − esperado.
 */
public final class CalculoCaja {

    /** No se instancia: solo métodos estáticos. */
    private CalculoCaja() {
    }

    /**
     * @param movimientos libro del turno
     * @return esperado por medio de pago (todos los medios, 0 si no tuvieron movimientos)
     */
    public static Map<MedioPago, BigDecimal> esperado(Collection<MovimientoCaja> movimientos) {
        Map<MedioPago, BigDecimal> esperado = ceros();
        for (MovimientoCaja m : movimientos) {
            esperado.merge(m.getMedioPago(), m.getMonto(), BigDecimal::add);
        }
        esperado.replaceAll((medio, valor) -> dinero(valor));
        return esperado;
    }

    /**
     * @param efectivo efectivo contado
     * @param otros    contado por medio distinto al efectivo ({@code null} o medios ausentes = 0)
     * @return contado por medio de pago (todos los medios)
     */
    public static Map<MedioPago, BigDecimal> contado(BigDecimal efectivo, Map<MedioPago, BigDecimal> otros) {
        Map<MedioPago, BigDecimal> contado = ceros();
        if (otros != null) {
            otros.forEach((medio, valor) -> {
                if (medio != null && medio != MedioPago.EFECTIVO && valor != null) {
                    contado.put(medio, dinero(valor));
                }
            });
        }
        contado.put(MedioPago.EFECTIVO, dinero(efectivo));
        return contado;
    }

    /**
     * @param contado  contado por medio
     * @param esperado esperado por medio
     * @return contado − esperado por medio (positivo = sobrante, negativo = faltante)
     */
    public static Map<MedioPago, BigDecimal> diferencias(Map<MedioPago, BigDecimal> contado,
            Map<MedioPago, BigDecimal> esperado) {
        Map<MedioPago, BigDecimal> diferencias = ceros();
        for (MedioPago medio : MedioPago.values()) {
            diferencias.put(medio, dinero(contado.getOrDefault(medio, BigDecimal.ZERO)
                    .subtract(esperado.getOrDefault(medio, BigDecimal.ZERO))));
        }
        return diferencias;
    }

    /**
     * @param diferencias diferencia por medio
     * @return suma de las diferencias
     */
    public static BigDecimal total(Map<MedioPago, BigDecimal> diferencias) {
        return dinero(diferencias.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /**
     * @param diferencias diferencia por medio
     * @param tolerancia  diferencia aceptada sin aprobación
     * @return {@code true} si alguna diferencia, sobrante o faltante, pasa la tolerancia
     */
    public static boolean requiereAprobacion(Map<MedioPago, BigDecimal> diferencias, BigDecimal tolerancia) {
        return diferencias.values().stream().anyMatch(d -> d.abs().compareTo(tolerancia) > 0);
    }

    /**
     * @param efectivo efectivo que debería haber en la caja
     * @param tope     tope de la caja (0 = sin aviso)
     * @return {@code true} si hay que retirar efectivo a la bóveda
     */
    public static boolean debeRetirar(BigDecimal efectivo, BigDecimal tope) {
        return tope != null && tope.signum() > 0 && efectivo.compareTo(tope) > 0;
    }

    /**
     * @return mapa con todos los medios en cero, en el orden del enum
     */
    private static Map<MedioPago, BigDecimal> ceros() {
        Map<MedioPago, BigDecimal> mapa = new EnumMap<>(MedioPago.class);
        for (MedioPago medio : MedioPago.values()) {
            mapa.put(medio, dinero(BigDecimal.ZERO));
        }
        return mapa;
    }

    /**
     * @param valor monto
     * @return monto con 2 decimales, redondeo {@code HALF_UP}
     */
    private static BigDecimal dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
