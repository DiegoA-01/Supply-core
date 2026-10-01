package com.proyecto.supply_core.auditoria.enums;

/**
 * Catálogo de acciones auditadas. Se guarda el {@code name()} en {@code auditoria.accion}.
 * Cada módulo nuevo agrega aquí sus acciones sensibles (aprobar, adjudicar, emitir orden, pagar...).
 */
public enum AccionAuditoria {
    /** Inicio de sesión (EXITO o FALLO). */
    LOGIN,
    /** Alta de usuario. */
    USUARIO_CREAR,
    /** Edición de datos o roles de un usuario. */
    USUARIO_EDITAR,
    /** Reactivación de un usuario. */
    USUARIO_ACTIVAR,
    /** Desactivación de un usuario. */
    USUARIO_DESACTIVAR,
    /** El administrador asignó una contraseña nueva. */
    USUARIO_RESTABLECER_PASSWORD,
    /** El usuario cambió su propia contraseña. */
    PASSWORD_CAMBIAR,
    /** Un proveedor se registró desde el portal (queda PENDIENTE). */
    PROVEEDOR_REGISTRAR,
    /** ADMIN_COMPRAS habilitó un proveedor. */
    PROVEEDOR_HABILITAR,
    /** ADMIN_COMPRAS rechazó un proveedor. */
    PROVEEDOR_RECHAZAR,
    /** ADMIN_COMPRAS suspendió un proveedor. */
    PROVEEDOR_SUSPENDER,
    /** Edición de los datos de la empresa. */
    EMPRESA_EDITAR,
    /** Alta de sede. */
    SEDE_CREAR,
    /** Edición de sede. */
    SEDE_EDITAR,
    /** Activación o desactivación de sede (el detalle dice cuál). */
    SEDE_CAMBIAR_ESTADO,
    /** Alta de centro de costo. */
    CENTRO_COSTO_CREAR,
    /** Edición de centro de costo. */
    CENTRO_COSTO_EDITAR,
    /** Activación o desactivación de centro de costo. */
    CENTRO_COSTO_CAMBIAR_ESTADO,
    /** Alta de política de aprobación. */
    POLITICA_CREAR,
    /** Edición de política de aprobación. */
    POLITICA_EDITAR,
    /** Activación o desactivación de política de aprobación. */
    POLITICA_CAMBIAR_ESTADO,
    /** Alta de categoría de producto. */
    CATEGORIA_CREAR,
    /** Edición de categoría de producto. */
    CATEGORIA_EDITAR,
    /** Activación o desactivación de categoría de producto. */
    CATEGORIA_CAMBIAR_ESTADO,
    /** Alta de unidad de medida. */
    UNIDAD_CREAR,
    /** Edición del nombre de una unidad de medida. */
    UNIDAD_EDITAR,
    /** Activación o desactivación de unidad de medida. */
    UNIDAD_CAMBIAR_ESTADO,
    /** Alta de producto. */
    PRODUCTO_CREAR,
    /** Edición de producto. */
    PRODUCTO_EDITAR,
    /** Activación o desactivación de producto. */
    PRODUCTO_CAMBIAR_ESTADO,
    /** Alta de bodega. */
    BODEGA_CREAR,
    /** Edición de bodega. */
    BODEGA_EDITAR,
    /** Activación o desactivación de bodega. */
    BODEGA_CAMBIAR_ESTADO,
    /** Ajuste manual de inventario (positivo o negativo), con su motivo. */
    INVENTARIO_AJUSTE,
    /** Subida de un documento (archivo) a un registro. */
    DOCUMENTO_SUBIR,
    /** Activación o desactivación de un canal de notificación para un evento. */
    CONFIG_NOTIFICACION_CAMBIAR
}
