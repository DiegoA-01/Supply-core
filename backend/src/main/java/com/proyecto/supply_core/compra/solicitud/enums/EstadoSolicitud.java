package com.proyecto.supply_core.compra.solicitud.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una solicitud de compra (ENUM de {@code solicitud_compra.estado}).
 * <pre>
 * BORRADOR → PENDIENTE_APROBACION → APROBADA | RECHAZADA | DEVUELTA
 * BORRADOR → APROBADA (si ninguna política de aprobación aplica)
 * DEVUELTA → (se edita) → PENDIENTE_APROBACION | APROBADA
 * APROBADA → EN_RFQ → ADJUDICADA → CERRADA
 * BORRADOR | DEVUELTA | PENDIENTE_APROBACION | APROBADA → CANCELADA
 * </pre>
 */
public enum EstadoSolicitud {
    /** Creada, editable, aún no enviada. */
    BORRADOR,
    /** Enviada; espera la decisión del rol aprobador de la política. */
    PENDIENTE_APROBACION,
    /** Aprobada; lista para crear una RFQ (paso 11). */
    APROBADA,
    /** El aprobador la devolvió con observaciones; el solicitante la corrige y la reenvía. */
    DEVUELTA,
    /** Rechazada por el aprobador (final). */
    RECHAZADA,
    /** Tiene una RFQ en curso. */
    EN_RFQ,
    /** Se adjudicó a un proveedor. */
    ADJUDICADA,
    /** Cancelada por el solicitante o compras (final). */
    CANCELADA,
    /** Proceso terminado (final). */
    CERRADA;

    /** Estados en los que el solicitante puede editar y enviar. */
    private static final Set<EstadoSolicitud> EDITABLES = EnumSet.of(BORRADOR, DEVUELTA);
    /** Estados desde los que se puede cancelar (antes de tener RFQ). */
    private static final Set<EstadoSolicitud> CANCELABLES = EnumSet.of(BORRADOR, DEVUELTA, PENDIENTE_APROBACION, APROBADA);

    /**
     * Indica si el solicitante puede editarla y enviarla.
     *
     * @return {@code true} en BORRADOR o DEVUELTA
     */
    public boolean esEditable() {
        return EDITABLES.contains(this);
    }

    /**
     * Indica si se puede cancelar.
     *
     * @return {@code true} si aún no pasó a RFQ ni terminó
     */
    public boolean esCancelable() {
        return CANCELABLES.contains(this);
    }
}
