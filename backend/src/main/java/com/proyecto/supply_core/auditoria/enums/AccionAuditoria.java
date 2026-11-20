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
    /** Un aprobador delegó sus decisiones en otro usuario durante un rango de fechas. */
    DELEGACION_CREAR,
    /** Se revocó una delegación de aprobación antes de que terminara. */
    DELEGACION_REVOCAR,
    /** El SLA de una solicitud pendiente venció y pasó a la política de mayor tramo. */
    SOLICITUD_ESCALAR,
    /** Se generó una solicitud en BORRADOR con las cantidades de la reorden sugerida. */
    REORDEN_GENERAR,
    /** Carga o actualización masiva de productos desde Excel. */
    PRODUCTO_CARGA_MASIVA,
    /** Alta de un contrato con un proveedor. */
    CONTRATO_CREAR,
    /** Cambio de la fecha de fin de un contrato. */
    CONTRATO_EDITAR,
    /** Anulación de un contrato. */
    CONTRATO_ANULAR,
    /** Emisión de una orden directa al precio pactado de un contrato. */
    ORDEN_EMITIR_DIRECTA,
    /** Alta del presupuesto de un centro de costo en un mes. */
    PRESUPUESTO_CREAR,
    /** Corrección del monto de un presupuesto. */
    PRESUPUESTO_EDITAR,
    /** Se envió una solicitud con excepción: excede el presupuesto o repite un producto ya pedido. */
    SOLICITUD_EXCEPCION,
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
    FACTURA_ARCHIVO,
    /** Se descargó un archivo exportado (Excel o PDF). */
    EXPORTAR_ARCHIVO,
    /** Se envió por correo un archivo exportado. */
    EXPORTAR_CORREO,
    /** Se pidió el correo de recuperación de una contraseña olvidada. */
    PASSWORD_RECUPERAR_PEDIR,
    /** Se cambió la contraseña con el token de recuperación. */
    PASSWORD_RECUPERAR_USAR,
    /** Un administrador de empresa activó su segundo factor (MFA). */
    MFA_ACTIVAR,
    /** Un administrador de empresa desactivó o reinició el MFA de un usuario. */
    MFA_DESACTIVAR,
    /** Se escribió un código de MFA incorrecto o repetido al iniciar sesión. */
    MFA_FALLO,
    /** El administrador de la empresa pidió un informe de compras. */
    INFORME_PEDIR,
    /** El área de compras envió un informe de compras (foto fija del periodo). */
    INFORME_ENVIAR,
    /** Un usuario interno pidió un producto a compras. */
    PEDIDO_INTERNO_CREAR,
    /** Compras tomó, atendió o descartó un pedido interno. */
    PEDIDO_INTERNO_GESTIONAR,
    /** Se configuró un impuesto de venta o una lista de precios. */
    PRECIO_CONFIGURAR,
    /** Se fijó un nuevo precio de venta (el anterior queda en el historial). */
    PRECIO_CAMBIAR,
    /** Un proveedor subió o reemplazó su catálogo. */
    CATALOGO_CARGAR,
    /** Compras confirmó o descartó la equivalencia de un artículo de catálogo. */
    CATALOGO_EQUIVALENCIA,
    /** Se creó un cliente (spec 002). */
    CLIENTE_CREAR,
    /** Se editó, desactivó o reactivó un cliente. */
    CLIENTE_EDITAR,
    /** Cambió el cupo de crédito, el plazo o la lista de precios de un cliente. */
    CLIENTE_CUPO_CAMBIAR,
    /** Se creó una caja (spec 003). */
    CAJA_CREAR,
    /** Se editó, desactivó o reactivó una caja. */
    CAJA_EDITAR,
    /** Se cambió la tolerancia de diferencia de caja. */
    CAJA_CONFIGURAR,
    /** Un cajero abrió su turno con una base. */
    TURNO_ABRIR,
    /** Retiro, gasto o ingreso de efectivo en un turno, con autorización. */
    TURNO_MOVIMIENTO,
    /** Un cajero cerró su turno a ciegas. */
    TURNO_CERRAR,
    /** Un supervisor aprobó la diferencia de un turno. */
    TURNO_APROBAR,
    /** Un supervisor autorizó con su PIN una acción del cajero. */
    AUTORIZACION_OTORGAR,
    /** Intento de autorización con un correo o PIN que no sirven, o con el PIN bloqueado. */
    AUTORIZACION_FALLO,
    /** Un supervisor fijó o cambió su PIN. */
    PIN_CAMBIAR,
    /** Se confirmó una venta (spec 004). */
    VENTA_CONFIRMAR,
    /** Un vendedor creó un pedido de venta. */
    PEDIDO_VENTA_CREAR,
    /** Se editó un pedido de venta pendiente. */
    PEDIDO_VENTA_EDITAR,
    /** Se descartó un pedido de venta sin cobrar. */
    PEDIDO_VENTA_DESCARTAR,
    /** El dueño cambió el límite de descuento de un rol. */
    LIMITE_DESCUENTO_CAMBIAR,
    /** Se imprimió el tiquete original de una venta (spec 005). */
    VENTA_IMPRIMIR,
    /** Se reimprimió el tiquete de una venta: sale con la marca COPIA. */
    VENTA_REIMPRIMIR,
    /** Se anuló una venta de un turno abierto con autorización (spec 006). */
    VENTA_ANULAR,
    /** Un cliente devolvió mercancía de una venta (spec 006). */
    VENTA_DEVOLVER,
    /** Se habilitó o se retiró la excepción de crédito de un cliente en mora (spec 007). */
    CREDITO_EXCEPCION,
    /** Un cliente abonó a su cartera en la caja. */
    ABONO_REGISTRAR
}
