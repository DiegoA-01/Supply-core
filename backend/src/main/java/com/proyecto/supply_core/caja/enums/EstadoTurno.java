package com.proyecto.supply_core.caja.enums;

/** Estado de un turno de caja. */
public enum EstadoTurno {
    /** El cajero está vendiendo; el esperado no se le muestra. */
    ABIERTO,
    /** Se contó y se guardó la diferencia; si pasó la tolerancia, espera la aprobación de un supervisor. */
    CERRADO,
    /** Un supervisor explicó y aprobó la diferencia que pasó la tolerancia. */
    APROBADO
}
