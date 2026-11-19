package com.proyecto.supply_core.security.context;

/**
 * Códigos de módulo sembrados en la migración V23. Usar siempre estas constantes en {@code @RequiereModulo},
 * nunca texto suelto. El dueño de la plataforma decide qué módulos tiene cada empresa.
 */
public final class Modulos {

    /** Productos, bodegas, stock y movimientos. */
    public static final String INVENTARIO = "INVENTARIO";
    /** Alta, aprobación y evaluación de proveedores. */
    public static final String PROVEEDORES = "PROVEEDORES";
    /** Solicitudes, aprobación, presupuesto, contratos, delegación y reorden. */
    public static final String COMPRAS = "COMPRAS";
    /** RFQ, cotizaciones, análisis y adjudicación. */
    public static final String RFQ_COTIZACIONES = "RFQ_COTIZACIONES";
    /** Lectura de cotizaciones y facturas con IA y explicación del ranking. */
    public static final String IA = "IA";
    /** Órdenes de compra, facturas, cruce de tres vías y pagos. */
    public static final String ORDENES_PAGOS = "ORDENES_PAGOS";
    /** Recepción de mercancía con control de calidad. */
    public static final String RECEPCION = "RECEPCION";
    /** Remisiones de salida. */
    public static final String DESPACHOS = "DESPACHOS";
    /** Gasto, inventario, proveedores y estado de cuenta. */
    public static final String REPORTES = "REPORTES";
    /** Consulta de la auditoría de la empresa. */
    public static final String AUDITORIA = "AUDITORIA";
    /** Precios de venta, listas de precios, impuestos y margen (fase F1 de ventas). */
    public static final String VENTAS = "VENTAS";
    /** Cajas, turnos, movimientos de caja, arqueo y cierre ciego (V31; se construye en la spec 003). */
    public static final String CAJA = "CAJA";
    /** Ventas a crédito, cupos, abonos y edades de cartera (V31; se construye en la spec 007). */
    public static final String CARTERA = "CARTERA";

    /** Clase utilitaria: no se instancia. */
    private Modulos() {
    }
}
