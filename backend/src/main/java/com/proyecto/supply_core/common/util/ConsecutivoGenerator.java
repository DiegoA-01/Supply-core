package com.proyecto.supply_core.common.util;

import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.proyecto.supply_core.common.exception.NegocioException;
import com.proyecto.supply_core.common.tenant.EmpresaContexto;

/**
 * Genera consecutivos legibles (SOL-000001, RFQ-000001, OC-000001) sin duplicados aunque
 * dos usuarios guarden al mismo tiempo: el incremento es atómico en MySQL
 * ({@code UPDATE ... LAST_INSERT_ID}) sobre la tabla {@code consecutivo} (V3, por empresa desde V17).
 */
@Component
public class ConsecutivoGenerator {

    /** Prefijo de solicitudes de compra. */
    public static final String SOLICITUD = "SOL";
    /** Prefijo de solicitudes de cotización. */
    public static final String RFQ = "RFQ";
    /** Prefijo de órdenes de compra. */
    public static final String ORDEN_COMPRA = "OC";
    /** Prefijo de despachos (remisiones de salida). */
    public static final String DESPACHO = "DES";

    /** Prefijo de los pedidos internos ("Necesito este producto"). */
    public static final String PEDIDO_INTERNO = "PED";
    /** Pedido de venta sin cobrar (spec 004). */
    public static final String PEDIDO_VENTA = "PV";
    /** Devolución de cliente (spec 006). */
    public static final String DEVOLUCION_VENTA = "DV";
    /** Abono de cartera (spec 007). */
    public static final String ABONO_CLIENTE = "AB";
    private static final Set<String> PREFIJOS = Set.of(SOLICITUD, RFQ, ORDEN_COMPRA, DESPACHO, PEDIDO_INTERNO, PEDIDO_VENTA, DEVOLUCION_VENTA, ABONO_CLIENTE);

    private final JdbcTemplate jdbc;

    /**
     * @param jdbc acceso JDBC (comparte la conexión de la transacción en curso)
     */
    public ConsecutivoGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Reserva el siguiente número del prefijo para la empresa del contexto (cada empresa lleva su propia
     * numeración; la fila se crea sola la primera vez). Debe correr dentro de una transacción para que
     * el INSERT ... ON DUPLICATE KEY y el {@code LAST_INSERT_ID()} usen la misma conexión; si no hay una, se abre.
     *
     * @param prefijo uno de {@link #SOLICITUD}, {@link #RFQ}, {@link #ORDEN_COMPRA}, {@link #DESPACHO}
     * @return consecutivo formateado, p. ej. {@code SOL-000001}
     * @throws NegocioException si el prefijo no es uno de los conocidos
     * @throws IllegalStateException si el hilo no tiene empresa en el contexto
     */
    @Transactional
    public String siguiente(String prefijo) {
        if (!PREFIJOS.contains(prefijo)) {
            throw new NegocioException("CONSECUTIVO", "No existe el consecutivo " + prefijo);
        }
        jdbc.update("INSERT INTO consecutivo (empresa_id, prefijo, ultimo) VALUES (?, ?, LAST_INSERT_ID(1)) "
                + "ON DUPLICATE KEY UPDATE ultimo = LAST_INSERT_ID(ultimo + 1)", EmpresaContexto.requerida(), prefijo);
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
