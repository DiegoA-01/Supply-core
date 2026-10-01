package com.proyecto.supply_core.common.util;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;

/**
 * Genera consecutivos legibles (SOL-000001, RFQ-000001, OC-000001) sin duplicados aunque
 * dos usuarios guarden al mismo tiempo: el incremento es atómico en MySQL
 * ({@code UPDATE ... LAST_INSERT_ID}) sobre la tabla {@code consecutivo} (migración V3).
 */
@Component
public class ConsecutivoGenerator {

    /** Prefijo de solicitudes de compra. */
    public static final String SOLICITUD = "SOL";
    /** Prefijo de solicitudes de cotización. */
    public static final String RFQ = "RFQ";
    /** Prefijo de órdenes de compra. */
    public static final String ORDEN_COMPRA = "OC";

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc acceso JDBC (comparte la conexión de la transacción en curso)
     */
    public ConsecutivoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva el siguiente número del prefijo. Debe correr dentro de una transacción para que
     * el UPDATE y el {@code LAST_INSERT_ID()} usen la misma conexión; si no hay una, se abre.
     *
     * @param prefijo uno de {@link #SOLICITUD}, {@link #RFQ}, {@link #ORDEN_COMPRA}
     * @return consecutivo formateado, p. ej. {@code SOL-000001}
     * @throws NegocioException si el prefijo no está registrado en la tabla {@code consecutivo}
     */
    @Transactional
    public String siguiente(String prefijo) {
        int filas = jdbc.update(
                "UPDATE consecutivo SET ultimo = LAST_INSERT_ID(ultimo + 1) WHERE prefijo = ?", prefijo);
        if (filas == 0) {
            throw new NegocioException("CONSECUTIVO", "No existe el consecutivo " + prefijo);
        }
        Long numero = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return formatear(prefijo, numero);
    }

    /**
     * Da formato fijo al número (seis dígitos con ceros a la izquierda).
     *
     * @param prefijo prefijo del documento
     * @param numero  número reservado
     * @return texto {@code PREFIJO-000000}
     */
    static String formatear(String prefijo, long numero) {
        return String.format("%s-%06d", prefijo, numero);
    }
}
