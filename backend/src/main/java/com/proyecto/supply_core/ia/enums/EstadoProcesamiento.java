package com.proyecto.supply_core.ia.enums;

/** Estado de la lectura de una cotización por archivo (ENUM de {@code procesamiento_ia.estado}). */
public enum EstadoProcesamiento {
    /** En cola para leerse. */
    PENDIENTE,
    /** La IA lo está leyendo. */
    PROCESANDO,
    /** Leído y todos los datos críticos tienen buena confianza (o ya se confirmaron). */
    COMPLETADO,
    /** Hay datos críticos dudosos, renglones no leídos o no hay IA: el comprador debe revisar o digitar. */
    REQUIERE_REVISION,
    /** La IA falló; se puede reintentar o digitar a mano. */
    ERROR;

    /**
     * Indica si la cotización ya está lista para el análisis.
     *
     * @return {@code true} solo en COMPLETADO
     */
    public boolean listo() {
        return this == COMPLETADO;
    }
}
