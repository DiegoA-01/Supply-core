package com.proyecto.supply_core.reporte.enums;

/** Cómo se agrupan las ventas en el reporte de ventas (spec 009). */
public enum AgrupacionVenta {
    /** Un renglón por día. */
    DIA,
    /** Un renglón por semana (empieza el lunes). */
    SEMANA,
    /** Un renglón por mes. */
    MES,
    /** Un renglón por producto. */
    PRODUCTO,
    /** Un renglón por categoría de producto. */
    CATEGORIA,
    /** Un renglón por cliente. */
    CLIENTE,
    /** Un renglón por vendedor (las ventas sin pedido van como «Venta directa»). */
    VENDEDOR,
    /** Un renglón por cajero. */
    CAJERO,
    /** Un renglón por caja. */
    CAJA,
    /** Un renglón por medio de pago (lo cobrado menos lo reembolsado por ese medio). */
    MEDIO_PAGO;

    /** @return {@code true} si los renglones son fechas y van en orden de calendario */
    public boolean esTiempo() {
        return this == DIA || this == SEMANA || this == MES;
    }
}
