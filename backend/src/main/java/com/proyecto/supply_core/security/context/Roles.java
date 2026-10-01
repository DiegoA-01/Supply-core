package com.proyecto.supply_core.security.context;

/**
 * Códigos de rol sembrados en la migración V2.
 * Usar siempre estas constantes en {@code @RequiereRol}, nunca texto suelto.
 */
public final class Roles {

    /** Configura usuarios, roles, seguridad y catálogos. No decide compras. */
    public static final String ADMIN_SISTEMA = "ADMIN_SISTEMA";
    /** Define políticas de compra, categorías y proveedores habilitados. */
    public static final String ADMIN_COMPRAS = "ADMIN_COMPRAS";
    /** Registra necesidades de compra. */
    public static final String SOLICITANTE = "SOLICITANTE";
    /** Gestiona RFQ, análisis y órdenes. */
    public static final String COMPRADOR = "COMPRADOR";
    /** Aprueba solicitudes y adjudicaciones según su límite. */
    public static final String APROBADOR = "APROBADOR";
    /** Recibe mercancía y registra movimientos de inventario. */
    public static final String ALMACENISTA = "ALMACENISTA";
    /** Registra pagos y comprobantes. */
    public static final String ADMINISTRATIVO = "ADMINISTRATIVO";
    /** Usuario del portal proveedor (tiene proveedor_id). */
    public static final String PROVEEDOR = "PROVEEDOR";
    /** Consulta trazabilidad e indicadores; solo lectura. */
    public static final String AUDITOR = "AUDITOR";

    /** Clase de constantes: no se instancia. */
    private Roles() {
    }
}
