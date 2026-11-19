package com.proyecto.supply_core.reorden.enums;

/**
 * Qué tan pronto hay que pedir un producto según su consumo y lo que tarda el proveedor. Ordenado de más a menos
 * urgente.
 */
public enum EstadoReabastecimiento {
    /** Aun pidiendo hoy, el producto se acaba antes de que llegue el pedido. */
    URGENTE,
    /** Ya está en el punto de pedido (o bajo el mínimo): hay que pedir hoy para no quedarse sin stock. */
    PEDIR_YA,
    /** Hay que pedir en los próximos días hábiles; la fecha límite está en {@code pedirAntesDe}. */
    PRONTO,
    /** Alcanza holgado: no hace falta pedir todavía. */
    OK,
    /** No tuvo salidas en el período y está sobre el mínimo: no hay consumo con qué calcular. */
    SIN_CONSUMO
}
