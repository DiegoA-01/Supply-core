package com.proyecto.supply_core.auditoria.enums;

/** Resultado de una acción auditada (ENUM {@code auditoria.resultado}). */
public enum ResultadoAuditoria {
    /** La acción se completó. */
    EXITO,
    /** La acción se intentó y fue rechazada (p. ej. login con clave errada). */
    FALLO
}
