package com.proyecto.supply_core.auditoria.enums;

/**
 * Entidades que pueden aparecer en la auditoría. Se guarda el {@code name()} en {@code auditoria.entidad}.
 * Cada módulo nuevo agrega aquí su entidad (SOLICITUD_COMPRA, RFQ, ORDEN_COMPRA...).
 */
public enum EntidadAuditada {
    /** Tabla {@code usuario}. */
    USUARIO,
    /** Tabla {@code proveedor}. */
    PROVEEDOR,
    /** Tabla {@code empresa}. */
    EMPRESA,
    /** Tabla {@code sede}. */
    SEDE,
    /** Tabla {@code centro_costo}. */
    CENTRO_COSTO,
    /** Tabla {@code delegacion_aprobacion}. */
    DELEGACION,
    /** Tabla {@code presupuesto}. */
    PRESUPUESTO,
    /** Tabla {@code contrato_proveedor}. */
    CONTRATO,
    /** Tabla {@code politica_aprobacion}. */
    POLITICA_APROBACION,
    /** Tabla {@code categoria_producto}. */
    CATEGORIA_PRODUCTO,
    /** Tabla {@code unidad_medida}. */
    UNIDAD_MEDIDA,
    /** Tabla {@code producto}. */
    PRODUCTO,
    /** Tabla {@code bodega}. */
    BODEGA,
    /** Tabla {@code movimiento_inventario}. */
    MOVIMIENTO_INVENTARIO,
    /** Tabla {@code documento}. */
    DOCUMENTO,
    /** Tabla {@code config_notificacion}. */
    CONFIG_NOTIFICACION,
    /** Tabla {@code solicitud_compra}. */
    SOLICITUD_COMPRA,
    /** Tabla {@code rfq}. */
    RFQ,
    /** Tabla {@code despacho}. */
    DESPACHO,
    /** Tabla {@code cotizacion}. */
    COTIZACION,
    /** Tabla {@code config_pesos_evaluacion}. */
    CONFIG_PESOS,
    /** Tabla {@code analisis_cotizacion}. */
    ANALISIS,
    /** Tabla {@code adjudicacion}. */
    ADJUDICACION,
    /** Tabla {@code orden_compra}. */
    ORDEN_COMPRA,
    /** Tabla {@code pago}. */
    PAGO,
    /** Tabla {@code recepcion}. */
    RECEPCION,
    /** Tabla {@code factura_proveedor}. */
    FACTURA
}
