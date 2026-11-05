package com.proyecto.supply_core.compra.solicitud.enums;

/** Decisión del aprobador (ENUM de {@code aprobacion_solicitud.decision}). */
public enum DecisionAprobacion {
    /** La solicitud sigue a RFQ. */
    APROBADA(EstadoSolicitud.APROBADA),
    /** Se rechaza definitivamente (observaciones obligatorias). */
    RECHAZADA(EstadoSolicitud.RECHAZADA),
    /** Vuelve al solicitante para corregir (observaciones obligatorias). */
    DEVUELTA(EstadoSolicitud.DEVUELTA);

    /** Estado en que queda la solicitud. */
    private final EstadoSolicitud estadoResultante;

    /**
     * @param estadoResultante estado en que queda la solicitud
     */
    DecisionAprobacion(EstadoSolicitud estadoResultante) {
        this.estadoResultante = estadoResultante;
    }

    /**
     * Estado en que queda la solicitud tras esta decisión.
     *
     * @return estado resultante
     */
    public EstadoSolicitud estadoResultante() {
        return estadoResultante;
    }

    /**
     * Indica si la decisión exige observaciones.
     *
     * @return {@code true} para RECHAZADA y DEVUELTA
     */
    public boolean exigeObservaciones() {
        return this != APROBADA;
    }
}
