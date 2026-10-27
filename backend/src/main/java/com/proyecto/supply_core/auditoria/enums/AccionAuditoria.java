package com.proyecto.supply_core.auditoria.enums;

/**
 * Catálogo de acciones auditadas. Se guarda el {@code name()} en {@code auditoria.accion}.
 * Cada módulo nuevo agrega aquí sus acciones sensibles (aprobar, adjudicar, emitir orden, pagar...).
 */
public enum AccionAuditoria {
    /** Inicio de sesión (EXITO o FALLO). */
    LOGIN,
    /** Cierre de sesión (una sesión o todas las del usuario). */
    LOGOUT,
    /** Se presentó un refresh token ya usado: se revocó la sesión completa. */
    REFRESH_REUTILIZADO,
    /** La cuenta se bloqueó temporalmente por intentos fallidos de login. */
    CUENTA_BLOQUEADA,
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
    COTIZACION_ENVIAR,
    /** Confirmación o corrección humana de un dato leído por la IA. */
    EXTRACCION_CONFIRMAR,
    /** Digitación manual de las líneas de una cotización que llegó por archivo. */
    COTIZACION_DIGITAR,
    /** Nueva versión de la matriz de pesos de evaluación. */
    PESOS_CAMBIAR,
    /** Generación del análisis comparativo (ranking en borrador). */
    ANALISIS_GENERAR,
    /** Publicación del ranking de un análisis. */
    ANALISIS_PUBLICAR,
    /** Adjudicación de una RFQ a una cotización. */
    ADJUDICACION_CREAR,
    /** Aprobación o rechazo de una adjudicación. */
    ADJUDICACION_DECIDIR,
    /** Emisión de una orden de compra (versión 1). */
    ORDEN_EMITIR,
    /** Nueva versión de una orden (la anterior queda REEMPLAZADA). */
    ORDEN_VERSIONAR,
    /** Cancelación de una orden. */
    ORDEN_CANCELAR,
    /** Respuesta del proveedor: aceptar, rechazar, preparar o despachar. */
    ORDEN_PROVEEDOR,
    /** Registro de un pago de una orden (UC-11). */
    PAGO_REGISTRAR,
    /** Comprobante adjuntado a un pago (cierra la evidencia pendiente). */
    PAGO_COMPROBANTE,
    /** Anulación de un pago registrado por error, con motivo. */
    PAGO_ANULAR,
    /** El almacenista registró una recepción de mercancía. */
    RECEPCION_REGISTRAR,
    /** Compras resolvió el excedente de una línea de recepción (aceptar o devolver). */
    RECEPCION_RESOLVER_EXCESO,
    /** Se cerró la recepción de una orden y se evaluó al proveedor. */
    RECEPCION_CERRAR,
    /** Se registró la factura de un proveedor y se cruzó con lo recibido. */
    FACTURA_REGISTRAR,
    /** Se volvió a cruzar una factura con lo recibido (la recepción cambió). */
    FACTURA_RECRUZAR,
    /** Compras aprobó una factura que no cuadraba, con motivo. */
    FACTURA_APROBAR,
    /** Se anuló una factura registrada por error. */
    FACTURA_ANULAR,
    /** Se adjuntó el PDF de una factura. */
    FACTURA_ARCHIVO
}
