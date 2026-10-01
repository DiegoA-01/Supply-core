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
    CONFIG_NOTIFICACION
}
