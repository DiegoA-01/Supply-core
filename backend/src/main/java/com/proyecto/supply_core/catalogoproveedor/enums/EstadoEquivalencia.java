package com.proyecto.supply_core.catalogoproveedor.enums;

/** Si un artículo del catálogo del proveedor corresponde a un producto del inventario. */
public enum EstadoEquivalencia {
    /** No se encontró ningún producto parecido. */
    SIN_REVISAR,
    /** El sistema propone un producto; falta que compras lo confirme. */
    SUGERIDA,
    /** Compras confirmó a qué producto corresponde: entra en las oportunidades. */
    CONFIRMADA,
    /** Compras indicó que no es de interés (no lo manejamos). */
    DESCARTADA
}
