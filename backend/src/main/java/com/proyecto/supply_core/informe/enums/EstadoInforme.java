package com.proyecto.supply_core.informe.enums;

/**
 * Estado de un informe de compras.
 */
public enum EstadoInforme {
    /** El administrador de la empresa lo pidió y el área de compras aún no lo envía. */
    PEDIDO,
    /** Ya se envió: guarda la foto fija del periodo y no vuelve a cambiar. */
    ENVIADO
}
