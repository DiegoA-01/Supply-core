package com.proyecto.supply_core.pedido.enums;

/** En qué va un pedido interno. */
public enum EstadoPedidoInterno {
    /** Recién hecho: compras aún no lo toma. */
    PENDIENTE,
    /** Compras lo está gestionando (traslado, compra o cotización). */
    EN_GESTION,
    /** Resuelto: llegó, se trasladó o se compró. */
    ATENDIDO,
    /** Compras decidió no hacerlo; la respuesta explica por qué. */
    DESCARTADO;

    /**
     * @return {@code true} si ya no se puede cambiar
     */
    public boolean cerrado() {
        return this == ATENDIDO || this == DESCARTADO;
    }
}
