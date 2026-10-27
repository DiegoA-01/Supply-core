package com.proyecto.supply_core.recepcion.enums;

/** Decisión de compras sobre el excedente de una línea de recepción. */
public enum DecisionExceso {
    /** Se queda con el excedente: entra al stock. */
    ACEPTAR,
    /** Se devuelve al proveedor: no entra al stock. */
    DEVOLVER
}
