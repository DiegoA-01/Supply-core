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
    /** Edición de datos de contacto o categorías de un proveedor (por él mismo o por ADMIN_COMPRAS). */
    PROVEEDOR_EDITAR,
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
    CONFIG_NOTIFICACION_CAMBIAR,
    /** Creación de una solicitud de compra (BORRADOR). */
    SOLICITUD_CREAR,
    /** Edición de una solicitud en BORRADOR o DEVUELTA. */
    SOLICITUD_EDITAR,
    /** Envío de una solicitud (pasa a aprobación o queda aprobada si no aplica política). */
    SOLICITUD_ENVIAR,
    /** Cancelación de una solicitud abierta. */
    SOLICITUD_CANCELAR,
    /** Decisión del aprobador: APROBADA, RECHAZADA o DEVUELTA. */
    SOLICITUD_DECIDIR,
    /** Creación de una RFQ en BORRADOR desde una solicitud aprobada. */
    RFQ_CREAR,
    /** Edición de una RFQ en BORRADOR (fecha límite, condiciones, invitados, especificaciones). */
    RFQ_EDITAR,
    /** Apertura de una RFQ: se envía a los proveedores invitados. */
    RFQ_ABRIR,
    /** Cierre de una RFQ (manual o automático por fecha límite). */
    RFQ_CERRAR,
    /** Cancelación de una RFQ. */
    RFQ_CANCELAR,
    /** Creación de un despacho (remisión de salida) en BORRADOR. */
    DESPACHO_CREAR,
    /** Edición de un despacho en BORRADOR. */
    DESPACHO_EDITAR,
    /** Confirmación de un despacho: descuenta el stock. */
    DESPACHO_CONFIRMAR,
    /** Anulación de un despacho (si estaba confirmado, devuelve el stock). */
    DESPACHO_ANULAR,
    /** Guardado del borrador de una cotización (formulario). */
    COTIZACION_GUARDAR,
    /** Envío de una cotización al comprador. */
    COTIZACION_ENVIAR
}
