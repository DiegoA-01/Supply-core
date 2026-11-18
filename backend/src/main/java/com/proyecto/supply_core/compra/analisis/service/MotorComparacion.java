package com.proyecto.supply_core.compra.analisis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Motor de comparación de cotizaciones: Java puro, sin BD ni IA (se prueba con ejemplos a mano).
 * <p>Cada criterio da un puntaje de 0 a 100; el score final es Σ puntaje × peso / 100.</p>
 * <ul>
 *   <li><b>PRECIO</b>: por cada producto de la RFQ, mejor precio normalizado ÷ precio de la oferta × 100;
 *       un producto no cotizado vale 0. Se promedia entre todos los productos.</li>
 *   <li><b>ENTREGA</b>: (menor plazo + 1) ÷ (plazo + 1) × 100. Sin plazo = 0 y alerta.</li>
 *   <li><b>GARANTIA</b>: garantía ÷ mayor garantía × 100 (si nadie ofrece garantía, 100 para todos).
 *       Sin dato = 0 y alerta.</li>
 *   <li><b>HISTORIAL</b>: score actual del proveedor (evaluaciones). Proveedor nuevo = 50 (neutro) y alerta.</li>
 *   <li><b>CALIDAD</b>: promedio del indicador de calidad de sus evaluaciones; sin evaluaciones = 50 (neutro).</li>
 *   <li><b>RIESGO</b>: porcentaje de productos cotizados (cobertura). Validez vencida = alerta ALTA.</li>
 * </ul>
 * <p>Las alertas (anomalías) nunca excluyen una oferta: solo informan.</p>
 */
public final class MotorComparacion {

    /** Puntaje neutro para proveedores sin historial ni evaluaciones de calidad. */
    static final BigDecimal NEUTRO = BigDecimal.valueOf(50);
    /** Cien. */
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    /** Desviación frente a la mediana que se considera atípica (50 %). */
    private static final BigDecimal DESVIO_ATIPICO = new BigDecimal("0.5");

    /** Clase de utilidades: no se instancia. */
    private MotorComparacion() {
    }

    /**
     * Calcula puntajes, score y ranking.
     *
     * @param lineasRfq ids de las líneas de la RFQ (productos pedidos)
     * @param ofertas   cotizaciones a comparar
     * @param pesos     peso por código de criterio (suman 100)
     * @param hoy       fecha de referencia (validez de las ofertas)
     * @return resultados ordenados del primero al último
     */
    public static List<Resultado> comparar(List<Long> lineasRfq, List<Oferta> ofertas, Map<String, BigDecimal> pesos,
            LocalDate hoy) {
        Map<Long, BigDecimal> mejorPrecio = new LinkedHashMap<>();
        Map<Long, BigDecimal> mediana = new LinkedHashMap<>();
        for (Long linea : lineasRfq) {
            List<BigDecimal> precios = ofertas.stream().map(o -> o.precios().get(linea)).filter(Objects::nonNull)
                    .sorted().toList();
            if (!precios.isEmpty()) {
                mejorPrecio.put(linea, precios.get(0));
                mediana.put(linea, precios.get(precios.size() / 2));
            }
        }
        Integer menorPlazo = ofertas.stream().map(Oferta::plazoDias).filter(Objects::nonNull).min(Integer::compare)
                .orElse(null);
        int mayorGarantia = ofertas.stream().map(Oferta::garantiaMeses).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0);
        int conPrecios = (int) ofertas.stream().filter(o -> !o.precios().isEmpty()).count();

        List<Resultado> resultados = new ArrayList<>();
        for (Oferta o : ofertas) {
            List<Anomalia> alertas = new ArrayList<>();
            Map<String, BigDecimal> puntajes = new LinkedHashMap<>();
            puntajes.put("PRECIO", puntajePrecio(o, lineasRfq, mejorPrecio));
            puntajes.put("ENTREGA", puntajeEntrega(o, menorPlazo, alertas));
            puntajes.put("GARANTIA", puntajeGarantia(o, mayorGarantia, alertas));
            puntajes.put("HISTORIAL", o.scoreHistorial() == null ? NEUTRO : escala(o.scoreHistorial()));
            puntajes.put("CALIDAD", o.calidadPromedio() == null ? NEUTRO : escala(o.calidadPromedio()));
            BigDecimal cobertura = lineasRfq.isEmpty() ? CIEN : escala(BigDecimal.valueOf(
                    lineasRfq.stream().filter(o.precios()::containsKey).count() * 100.0 / lineasRfq.size()));
            puntajes.put("RIESGO", cobertura);

            if (o.scoreHistorial() == null) {
                alertas.add(new Anomalia("PROVEEDOR_SIN_HISTORIAL", "BAJA",
                        "Proveedor sin evaluaciones previas: historial y calidad se puntúan neutros (50)"));
            }
            if (cobertura.compareTo(CIEN) < 0) {
                alertas.add(new Anomalia("COBERTURA_INCOMPLETA", "MEDIA",
                        "Cotizó el " + cobertura.stripTrailingZeros().toPlainString() + " % de los productos pedidos"));
            }
            if (o.validezHasta() != null && o.validezHasta().isBefore(hoy)) {
                alertas.add(new Anomalia("VALIDEZ_VENCIDA", "ALTA",
                        "La oferta venció el " + o.validezHasta() + "; confirme precios con el proveedor"));
            }
            if (conPrecios >= 3) {
                alertasPrecio(o, mediana, alertas);
            }

            Map<String, BigDecimal> ponderados = new LinkedHashMap<>();
            BigDecimal score = BigDecimal.ZERO;
            for (Map.Entry<String, BigDecimal> p : puntajes.entrySet()) {
                BigDecimal peso = pesos.getOrDefault(p.getKey(), BigDecimal.ZERO);
                BigDecimal ponderado = p.getValue().multiply(peso).divide(CIEN, 2, RoundingMode.HALF_UP);
                ponderados.put(p.getKey(), ponderado);
                score = score.add(ponderado);
            }
            resultados.add(new Resultado(o.cotizacionId(), o.proveedor(), o.total(), puntajes, ponderados, score, 0,
                    alertas));
        }
        resultados.sort(Comparator.comparing(Resultado::scoreFinal).reversed()
                .thenComparing(r -> r.costoTotal() == null ? BigDecimal.valueOf(Long.MAX_VALUE) : r.costoTotal())
                .thenComparing(Resultado::cotizacionId));
        List<Resultado> ordenados = new ArrayList<>();
        for (int i = 0; i < resultados.size(); i++) {
            ordenados.add(resultados.get(i).conPosicion(i + 1));
        }
        return ordenados;
    }

    /**
     * Puntaje de precio: promedio por producto de mejor precio ÷ precio propio.
     *
     * @param o           oferta
     * @param lineasRfq   productos pedidos
     * @param mejorPrecio mejor precio por producto
     * @return 0–100
     */
    static BigDecimal puntajePrecio(Oferta o, List<Long> lineasRfq, Map<Long, BigDecimal> mejorPrecio) {
        if (lineasRfq.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal suma = BigDecimal.ZERO;
        for (Long linea : lineasRfq) {
            BigDecimal propio = o.precios().get(linea);
            if (propio == null) {
                continue; // producto no cotizado: aporta 0
            }
            BigDecimal mejor = mejorPrecio.get(linea);
            suma = suma.add(propio.signum() == 0 ? CIEN : mejor.multiply(CIEN).divide(propio, 6, RoundingMode.HALF_UP));
        }
        return suma.divide(BigDecimal.valueOf(lineasRfq.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Puntaje de entrega.
     *
     * @param o          oferta
     * @param menorPlazo menor plazo ofrecido (o {@code null})
     * @param alertas    acumulador de alertas
     * @return 0–100
     */
    private static BigDecimal puntajeEntrega(Oferta o, Integer menorPlazo, List<Anomalia> alertas) {
        if (o.plazoDias() == null) {
            alertas.add(new Anomalia("SIN_PLAZO", "BAJA", "No informó plazo de entrega"));
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(menorPlazo + 1L).multiply(CIEN)
                .divide(BigDecimal.valueOf(o.plazoDias() + 1L), 2, RoundingMode.HALF_UP);
    }

    /**
     * Puntaje de garantía.
     *
     * @param o             oferta
     * @param mayorGarantia mayor garantía ofrecida (meses)
     * @param alertas       acumulador de alertas
     * @return 0–100
     */
    private static BigDecimal puntajeGarantia(Oferta o, int mayorGarantia, List<Anomalia> alertas) {
        if (o.garantiaMeses() == null) {
            alertas.add(new Anomalia("SIN_GARANTIA", "BAJA", "No informó garantía"));
            return BigDecimal.ZERO;
        }
        if (mayorGarantia == 0) {
            return CIEN.setScale(2);
        }
        return BigDecimal.valueOf(o.garantiaMeses()).multiply(CIEN)
                .divide(BigDecimal.valueOf(mayorGarantia), 2, RoundingMode.HALF_UP);
    }

    /**
     * Precio atípico frente a la mediana de las ofertas (solo con 3 o más ofertas).
     *
     * @param o       oferta
     * @param mediana mediana por producto
     * @param alertas acumulador de alertas
     */
    private static void alertasPrecio(Oferta o, Map<Long, BigDecimal> mediana, List<Anomalia> alertas) {
        for (Map.Entry<Long, BigDecimal> p : o.precios().entrySet()) {
            BigDecimal med = mediana.get(p.getKey());
            if (med == null || med.signum() == 0) {
                continue;
            }
            BigDecimal desvio = p.getValue().subtract(med).divide(med, 4, RoundingMode.HALF_UP);
            if (desvio.compareTo(DESVIO_ATIPICO) > 0) {
                alertas.add(new Anomalia("PRECIO_ATIPICO", "MEDIA", "Precio de la línea " + p.getKey()
                        + " más de 50 % por encima de la mediana de las ofertas"));
            } else if (desvio.compareTo(DESVIO_ATIPICO.negate()) < 0) {
                alertas.add(new Anomalia("PRECIO_ATIPICO", "MEDIA", "Precio de la línea " + p.getKey()
                        + " más de 50 % por debajo de la mediana: verifique que sea correcto"));
            }
        }
    }

    /**
     * Acota a 0–100 con 2 decimales.
     *
     * @param valor valor
     * @return valor acotado
     */
    private static BigDecimal escala(BigDecimal valor) {
        return valor.max(BigDecimal.ZERO).min(CIEN).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Oferta a comparar.
     *
     * @param cotizacionId    cotización
     * @param proveedor       razón social
     * @param total           total con impuestos (informativo y desempate)
     * @param plazoDias       plazo de entrega o {@code null}
     * @param garantiaMeses   garantía o {@code null}
     * @param validezHasta    validez de la oferta o {@code null}
     * @param scoreHistorial  score actual del proveedor o {@code null}
     * @param calidadPromedio promedio de calidad de sus evaluaciones o {@code null}
     * @param precios         precio normalizado por línea de la RFQ (solo las cotizadas)
     */
    public record Oferta(Long cotizacionId, String proveedor, BigDecimal total, Integer plazoDias, Integer garantiaMeses,
            LocalDate validezHasta, BigDecimal scoreHistorial, BigDecimal calidadPromedio, Map<Long, BigDecimal> precios) {
    }

    /**
     * Resultado de una oferta.
     *
     * @param cotizacionId cotización
     * @param proveedor    razón social
     * @param costoTotal   total con impuestos
     * @param puntajes     puntaje 0–100 por criterio
     * @param ponderados   puntaje × peso / 100 por criterio
     * @param scoreFinal   suma de ponderados
     * @param posicion     lugar en el ranking (1 = recomendada)
     * @param anomalias    alertas (no excluyen)
     */
    public record Resultado(Long cotizacionId, String proveedor, BigDecimal costoTotal, Map<String, BigDecimal> puntajes,
            Map<String, BigDecimal> ponderados, BigDecimal scoreFinal, int posicion, List<Anomalia> anomalias) {

        /**
         * Copia con la posición asignada.
         *
         * @param nueva posición en el ranking
         * @return resultado con posición
         */
        Resultado conPosicion(int nueva) {
            return new Resultado(cotizacionId, proveedor, costoTotal, puntajes, ponderados, scoreFinal, nueva, anomalias);
        }
    }

    /**
     * Alerta sobre una oferta.
     *
     * @param tipo        PRECIO_ATIPICO, COBERTURA_INCOMPLETA...
     * @param severidad   BAJA, MEDIA o ALTA
     * @param descripcion texto para el comprador
     */
    public record Anomalia(String tipo, String severidad, String descripcion) {
    }
}
