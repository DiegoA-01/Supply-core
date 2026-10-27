package com.proyecto.supply_core.compra.orden.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una orden de compra (ENUM de {@code orden_compra.estado}).
 * <pre>
 * EMITIDA → ACEPTADA_PROVEEDOR → EN_PREPARACION → DESPACHADA → RECIBIDA_PARCIAL | RECIBIDA_TOTAL → CERRADA
 * RECIBIDA_PARCIAL → CERRADA (cierre con faltante, con motivo)
 * EMITIDA → RECHAZADA_PROVEEDOR
 * EMITIDA | ACEPTADA_PROVEEDOR | EN_PREPARACION | RECHAZADA_PROVEEDOR → CANCELADA
 * EMITIDA | ACEPTADA_PROVEEDOR | EN_PREPARACION | RECHAZADA_PROVEEDOR → REEMPLAZADA (nueva versión)
 * </pre>
 * Las recepciones (RECIBIDA_*) y el cierre las maneja {@code RecepcionService}.
 */
public enum EstadoOrden {
    /** Enviada al proveedor; espera su respuesta. */
    EMITIDA,
    /** El proveedor la aceptó. */
    ACEPTADA_PROVEEDOR,
    /** El proveedor la rechazó (con motivo); compras decide: nueva versión o cancelar. */
    RECHAZADA_PROVEEDOR,
    /** El proveedor la está alistando. */
    EN_PREPARACION,
    /** Enviada a la bodega destino. */
    DESPACHADA,
    /** Llegó una parte. */
    RECIBIDA_PARCIAL,
    /** Llegó todo. */
    RECIBIDA_TOTAL,
    /** Terminada. */
    CERRADA,
    /** Cancelada por compras (final). */
    CANCELADA,
    /** Hay una versión más nueva (final). */
    REEMPLAZADA;

    /** Transiciones permitidas por acción directa (proveedor y compras). */
    private static final Map<EstadoOrden, Set<EstadoOrden>> TRANSICIONES = Map.of(
            EMITIDA, EnumSet.of(ACEPTADA_PROVEEDOR, RECHAZADA_PROVEEDOR, CANCELADA, REEMPLAZADA),
            ACEPTADA_PROVEEDOR, EnumSet.of(EN_PREPARACION, CANCELADA, REEMPLAZADA),
            EN_PREPARACION, EnumSet.of(DESPACHADA, CANCELADA, REEMPLAZADA),
            RECHAZADA_PROVEEDOR, EnumSet.of(CANCELADA, REEMPLAZADA),
            DESPACHADA, EnumSet.of(RECIBIDA_PARCIAL, RECIBIDA_TOTAL),
            RECIBIDA_PARCIAL, EnumSet.of(RECIBIDA_PARCIAL, RECIBIDA_TOTAL, CERRADA),
            RECIBIDA_TOTAL, EnumSet.of(CERRADA));

    /**
     * Indica si se puede pasar a otro estado.
     *
     * @param destino estado destino
     * @return {@code true} si la transición está permitida
     */
    public boolean puedePasarA(EstadoOrden destino) {
        return TRANSICIONES.getOrDefault(this, Set.of()).contains(destino);
    }
}
