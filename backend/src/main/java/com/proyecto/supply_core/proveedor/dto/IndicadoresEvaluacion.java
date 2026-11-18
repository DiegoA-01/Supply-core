package com.proyecto.supply_core.proveedor.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Indicadores de una evaluación (UC-14), cada uno de 0 a 100 (más alto = mejor) o {@code null}
 * si no hay datos. Los calcula la recepción (paso 17); no llegan por la API.
 *
 * @param puntualidad          entrega frente a la fecha esperada
 * @param cumplimientoCantidad cantidad recibida frente a la pedida
 * @param incidencias          ausencia de incidencias (100 = ninguna)
 * @param calidad              mercancía aceptada frente a la recibida
 * @param precio               precio frente al de referencia
 * @param respuesta            rapidez de respuesta a RFQ y orden
 */
public record IndicadoresEvaluacion(
        BigDecimal puntualidad,
        BigDecimal cumplimientoCantidad,
        BigDecimal incidencias,
        BigDecimal calidad,
        BigDecimal precio,
        BigDecimal respuesta) {

    /** Valor máximo de un indicador. */
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    /**
     * Valida el rango y normaliza la escala a 2 decimales.
     *
     * @throws IllegalArgumentException si algún indicador está fuera de 0–100
     */
    public IndicadoresEvaluacion {
        puntualidad = normalizar("puntualidad", puntualidad);
        cumplimientoCantidad = normalizar("cumplimientoCantidad", cumplimientoCantidad);
        incidencias = normalizar("incidencias", incidencias);
        calidad = normalizar("calidad", calidad);
        precio = normalizar("precio", precio);
        respuesta = normalizar("respuesta", respuesta);
    }

    /**
     * Score = promedio simple de los indicadores con datos (2 decimales, HALF_UP).
     * Los que no tienen datos no cuentan; si ninguno tiene datos el score es {@code null}.
     *
     * @return score de 0 a 100 o {@code null}
     */
    public BigDecimal score() {
        var conDatos = Stream.of(puntualidad, cumplimientoCantidad, incidencias, calidad, precio, respuesta)
                .filter(Objects::nonNull).toList();
        if (conDatos.isEmpty()) {
            return null;
        }
        return conDatos.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(conDatos.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Verifica que el valor esté entre 0 y 100.
     *
     * @param nombre nombre del indicador (para el mensaje)
     * @param valor  valor o {@code null}
     * @return valor con 2 decimales o {@code null}
     * @throws IllegalArgumentException si está fuera de rango
     */
    private static BigDecimal normalizar(String nombre, BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0 || valor.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException(nombre + " debe estar entre 0 y 100: " + valor.toPlainString());
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
