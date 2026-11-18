package com.proyecto.supply_core.recepcion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.proyecto.supply_core.proveedor.dto.IndicadoresEvaluacion;

/**
 * Cálculos de la recepción en Java puro (la regla de oro: el sistema calcula, no la IA ni el usuario).
 * Reparte lo recibido en rechazado, aceptado y excedente, y arma los indicadores de evaluación del proveedor.
 */
public final class CalculadoraRecepcion {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** Puntos que pierde la puntualidad por cada día de atraso. */
    private static final BigDecimal PUNTOS_POR_DIA_TARDE = BigDecimal.TEN;
    private static final int ESCALA = 4;

    /** Clase de cálculo: no se instancia. */
    private CalculadoraRecepcion() {
    }

    /**
     * Reparto de una línea en una recepción.
     *
     * @param aceptada  lo bueno que cabe en lo pedido (sube el stock)
     * @param excedente lo bueno que sobra sobre lo pedido (queda en revisión)
     * @param faltante  lo que aún falta de la línea después de esta recepción
     */
    public record Reparto(BigDecimal aceptada, BigDecimal excedente, BigDecimal faltante) {
    }

    /**
     * Totales de una línea de la orden a lo largo de todas sus recepciones, para evaluar al proveedor.
     *
     * @param pedido    cantidad pedida
     * @param aceptado  total aceptado
     * @param recibido  total recibido
     * @param rechazado total rechazado
     * @param excedente total que llegó de más
     */
    public record Totales(BigDecimal pedido, BigDecimal aceptado, BigDecimal recibido, BigDecimal rechazado,
            BigDecimal excedente) {
    }

    /**
     * Reparte lo que llegó de una línea: lo rechazado se descarta, lo bueno entra hasta completar lo que
     * faltaba del pedido y lo que sobre es excedente.
     *
     * @param pedido         cantidad pedida de la línea
     * @param aceptadoPrevio lo ya aceptado en recepciones anteriores
     * @param recibida       lo que llegó en esta recepción
     * @param rechazada      parte de lo recibido que no sirve
     * @return aceptado, excedente y faltante de la línea
     * @throws IllegalArgumentException si lo rechazado supera lo recibido
     */
    public static Reparto repartir(BigDecimal pedido, BigDecimal aceptadoPrevio, BigDecimal recibida,
            BigDecimal rechazada) {
        if (rechazada.compareTo(recibida) > 0) {
            throw new IllegalArgumentException("Lo rechazado no puede superar lo recibido");
        }
        BigDecimal buenas = recibida.subtract(rechazada);
        BigDecimal pendiente = pedido.subtract(aceptadoPrevio).max(BigDecimal.ZERO);
        BigDecimal aceptada = buenas.min(pendiente);
        BigDecimal excedente = buenas.subtract(aceptada);
        BigDecimal faltante = pendiente.subtract(aceptada);
        return new Reparto(aceptada, excedente, faltante);
    }

    /**
     * Indicadores de evaluación del proveedor al cerrar la recepción (0 a 100, o {@code null} si no hay datos).
     * <ul>
     *   <li><b>Puntualidad:</b> 100 si la última recepción fue en la fecha esperada o antes; pierde 10 puntos
     *       por día de atraso (mínimo 0).</li>
     *   <li><b>Cumplimiento de cantidad:</b> promedio por línea de lo aceptado frente a lo pedido (tope 100 %).</li>
     *   <li><b>Incidencias:</b> 100 menos el porcentaje de líneas con rechazo, faltante o excedente.</li>
     *   <li><b>Calidad:</b> promedio por línea de lo bueno frente a lo recibido (solo líneas que llegaron).</li>
     *   <li><b>Precio y respuesta:</b> sin datos todavía ({@code null}).</li>
     * </ul>
     *
     * @param lineas           totales por línea de la orden
     * @param fechaEsperada    fecha de entrega esperada de la orden
     * @param ultimaRecepcion  fecha de la última recepción ({@code null} si no hubo)
     * @return indicadores
     */
    public static IndicadoresEvaluacion indicadores(List<Totales> lineas, LocalDate fechaEsperada,
            LocalDate ultimaRecepcion) {
        return new IndicadoresEvaluacion(puntualidad(fechaEsperada, ultimaRecepcion), cumplimiento(lineas),
                incidencias(lineas), calidad(lineas), null, null);
    }

    /**
     * Puntualidad según los días de atraso.
     *
     * @param esperada fecha esperada
     * @param ultima   fecha de la última recepción
     * @return 0 a 100 o {@code null} si no hubo recepción
     */
    static BigDecimal puntualidad(LocalDate esperada, LocalDate ultima) {
        if (ultima == null) {
            return null;
        }
        long diasTarde = ChronoUnit.DAYS.between(esperada, ultima);
        if (diasTarde <= 0) {
            return CIEN;
        }
        return CIEN.subtract(PUNTOS_POR_DIA_TARDE.multiply(BigDecimal.valueOf(diasTarde))).max(BigDecimal.ZERO);
    }

    private static BigDecimal cumplimiento(List<Totales> lineas) {
        List<BigDecimal> razones = lineas.stream().filter(l -> l.pedido().signum() > 0)
                .map(l -> l.aceptado().divide(l.pedido(), ESCALA, RoundingMode.HALF_UP).min(BigDecimal.ONE)).toList();
        return promedioPorcentual(razones);
    }

    private static BigDecimal incidencias(List<Totales> lineas) {
        if (lineas.isEmpty()) {
            return null;
        }
        long conIncidencia = lineas.stream().filter(l -> l.rechazado().signum() > 0
                || l.aceptado().compareTo(l.pedido()) < 0 || l.excedente().signum() > 0).count();
        BigDecimal fraccion = BigDecimal.valueOf(conIncidencia).divide(BigDecimal.valueOf(lineas.size()), ESCALA,
                RoundingMode.HALF_UP);
        return CIEN.subtract(fraccion.multiply(CIEN));
    }

    private static BigDecimal calidad(List<Totales> lineas) {
        List<BigDecimal> razones = lineas.stream().filter(l -> l.recibido().signum() > 0)
                .map(l -> l.recibido().subtract(l.rechazado()).divide(l.recibido(), ESCALA, RoundingMode.HALF_UP)).toList();
        return promedioPorcentual(razones);
    }

    private static BigDecimal promedioPorcentual(List<BigDecimal> razones) {
        if (razones.isEmpty()) {
            return null;
        }
        BigDecimal suma = razones.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(razones.size()), ESCALA, RoundingMode.HALF_UP).multiply(CIEN);
    }
}
