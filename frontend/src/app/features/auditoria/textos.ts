/**
 * Texto en español de cada acción y de cada entidad de la auditoría de la empresa. Las claves son exactamente los
 * códigos del backend (`AccionAuditoria` y `EntidadAuditada`); si el backend agrega uno, la prueba de esta carpeta avisa.
 */
export const ACCIONES: Record<string, string> = {
  // Sesión y cuenta
  LOGIN: 'Ingresó', LOGOUT: 'Cerró sesión', REFRESH_REUTILIZADO: 'Intento de reutilizar una sesión ya cerrada',
  CUENTA_BLOQUEADA: 'Cuenta bloqueada por intentos fallidos', PASSWORD_CAMBIAR: 'Cambió su contraseña',
  PASSWORD_RECUPERAR_PEDIR: 'Pidió recuperar su contraseña', PASSWORD_RECUPERAR_USAR: 'Cambió su contraseña con el código de recuperación',
  MFA_ACTIVAR: 'Activó el segundo factor', MFA_DESACTIVAR: 'Reinició el segundo factor', MFA_FALLO: 'Falló el código del segundo factor',
  INFORME_PEDIR: 'Pidió un informe de compras', INFORME_ENVIAR: 'Envió un informe de compras',
  // Usuarios, empresa y estructura
  USUARIO_CREAR: 'Creó un usuario', USUARIO_EDITAR: 'Editó un usuario', USUARIO_ACTIVAR: 'Activó un usuario', USUARIO_DESACTIVAR: 'Desactivó un usuario',
  USUARIO_RESTABLECER_PASSWORD: 'Restableció la contraseña de un usuario', EMPRESA_EDITAR: 'Editó los datos de la empresa',
  SEDE_CREAR: 'Creó una sede', SEDE_EDITAR: 'Editó una sede', SEDE_CAMBIAR_ESTADO: 'Activó o desactivó una sede',
  CENTRO_COSTO_CREAR: 'Creó un centro de costo', CENTRO_COSTO_EDITAR: 'Editó un centro de costo', CENTRO_COSTO_CAMBIAR_ESTADO: 'Activó o desactivó un centro de costo',
  BODEGA_CREAR: 'Creó una bodega', BODEGA_EDITAR: 'Editó una bodega', BODEGA_CAMBIAR_ESTADO: 'Activó o desactivó una bodega',
  CATEGORIA_CREAR: 'Creó una categoría', CATEGORIA_EDITAR: 'Editó una categoría', CATEGORIA_CAMBIAR_ESTADO: 'Activó o desactivó una categoría',
  UNIDAD_CREAR: 'Creó una unidad de medida', UNIDAD_EDITAR: 'Editó una unidad de medida', UNIDAD_CAMBIAR_ESTADO: 'Activó o desactivó una unidad de medida',
  CONFIG_NOTIFICACION_CAMBIAR: 'Cambió un aviso o correo',
  // Reglas de compra
  POLITICA_CREAR: 'Creó una política de aprobación', POLITICA_EDITAR: 'Editó una política de aprobación', POLITICA_CAMBIAR_ESTADO: 'Activó o desactivó una política',
  PESOS_CAMBIAR: 'Cambió los pesos de evaluación', PRESUPUESTO_CREAR: 'Creó un presupuesto', PRESUPUESTO_EDITAR: 'Cambió el monto de un presupuesto',
  CONTRATO_CREAR: 'Creó un contrato', CONTRATO_EDITAR: 'Cambió la vigencia de un contrato', CONTRATO_ANULAR: 'Anuló un contrato',
  DELEGACION_CREAR: 'Delegó sus aprobaciones', DELEGACION_REVOCAR: 'Revocó una delegación',
  // Proveedores
  PROVEEDOR_REGISTRAR: 'Se registró un proveedor', PROVEEDOR_HABILITAR: 'Habilitó un proveedor', PROVEEDOR_RECHAZAR: 'Rechazó un proveedor',
  PROVEEDOR_SUSPENDER: 'Suspendió un proveedor', PROVEEDOR_EDITAR: 'Editó un proveedor',
  // Productos e inventario
  PRODUCTO_CREAR: 'Creó un producto', PRODUCTO_EDITAR: 'Editó un producto', PRODUCTO_CAMBIAR_ESTADO: 'Activó o desactivó un producto',
  PRODUCTO_CARGA_MASIVA: 'Cargó productos desde Excel', REORDEN_GENERAR: 'Generó un borrador de reorden',
  PEDIDO_INTERNO_CREAR: 'Pidió un producto a compras', PEDIDO_INTERNO_GESTIONAR: 'Atendió o descartó un pedido interno',
  CATALOGO_CARGAR: 'Un proveedor subió su catálogo', CATALOGO_EQUIVALENCIA: 'Confirmó o descartó una equivalencia de catálogo',
  // Ventas
  PRECIO_CONFIGURAR: 'Configuró un impuesto o una lista de precios', PRECIO_CAMBIAR: 'Cambió un precio de venta',
  CLIENTE_CREAR: 'Creó un cliente', CLIENTE_EDITAR: 'Editó un cliente', CLIENTE_CUPO_CAMBIAR: 'Cambió el cupo, el plazo o la lista de un cliente',
  // Caja
  CAJA_CREAR: 'Creó una caja', CAJA_EDITAR: 'Editó una caja', CAJA_CONFIGURAR: 'Cambió la tolerancia de diferencia de caja',
  TURNO_ABRIR: 'Abrió un turno de caja', TURNO_MOVIMIENTO: 'Registró un retiro, gasto o ingreso de caja',
  TURNO_CERRAR: 'Cerró un turno de caja', TURNO_APROBAR: 'Aprobó la diferencia de un turno',
  AUTORIZACION_OTORGAR: 'Autorizó con su PIN una acción de caja', AUTORIZACION_FALLO: 'Intento de autorización fallido',
  PIN_CAMBIAR: 'Fijó o cambió su PIN de supervisor',
  INVENTARIO_AJUSTE: 'Movió el inventario (salida, ajuste o traslado)', DOCUMENTO_SUBIR: 'Subió un documento',
  // Solicitudes
  SOLICITUD_CREAR: 'Creó una solicitud', SOLICITUD_EDITAR: 'Editó una solicitud', SOLICITUD_ENVIAR: 'Envió una solicitud', SOLICITUD_CANCELAR: 'Canceló una solicitud',
  SOLICITUD_DECIDIR: 'Decidió una solicitud', SOLICITUD_ESCALAR: 'Se escaló una solicitud por plazo vencido', SOLICITUD_EXCEPCION: 'Envió una solicitud con excepción',
  // Cotizaciones y análisis
  RFQ_CREAR: 'Creó una RFQ', RFQ_EDITAR: 'Editó una RFQ', RFQ_ABRIR: 'Abrió una RFQ', RFQ_CERRAR: 'Cerró una RFQ', RFQ_CANCELAR: 'Canceló una RFQ',
  COTIZACION_GUARDAR: 'Guardó una cotización', COTIZACION_ENVIAR: 'Envió una cotización', COTIZACION_DIGITAR: 'Digitó los precios de una cotización',
  EXTRACCION_CONFIRMAR: 'Confirmó un dato leído por la IA', ANALISIS_GENERAR: 'Generó un análisis', ANALISIS_PUBLICAR: 'Publicó un análisis',
  ADJUDICACION_CREAR: 'Adjudicó una cotización', ADJUDICACION_DECIDIR: 'Decidió una adjudicación',
  // Órdenes, despachos y recepción
  ORDEN_EMITIR: 'Emitió una orden', ORDEN_EMITIR_DIRECTA: 'Emitió una orden directa por contrato', ORDEN_VERSIONAR: 'Creó una versión nueva de una orden',
  ORDEN_CANCELAR: 'Canceló una orden', ORDEN_PROVEEDOR: 'El proveedor respondió una orden',
  DESPACHO_CREAR: 'Creó un despacho', DESPACHO_EDITAR: 'Editó un despacho', DESPACHO_CONFIRMAR: 'Confirmó un despacho', DESPACHO_ANULAR: 'Anuló un despacho',
  RECEPCION_REGISTRAR: 'Registró una recepción', RECEPCION_RESOLVER_EXCESO: 'Resolvió un excedente de recepción', RECEPCION_CERRAR: 'Cerró una recepción',
  // Facturas y pagos
  FACTURA_REGISTRAR: 'Registró una factura', FACTURA_RECRUZAR: 'Cruzó de nuevo una factura', FACTURA_APROBAR: 'Aprobó una factura en revisión',
  FACTURA_ANULAR: 'Anuló una factura', FACTURA_ARCHIVO: 'Adjuntó el archivo de una factura',
  PAGO_REGISTRAR: 'Registró un pago', PAGO_COMPROBANTE: 'Adjuntó el comprobante de un pago', PAGO_ANULAR: 'Anuló un pago',
  // Exportaciones
  EXPORTAR_ARCHIVO: 'Descargó un archivo', EXPORTAR_CORREO: 'Se envió un archivo por correo',
};

export const ENTIDADES: Record<string, string> = {
  USUARIO: 'Usuario', PROVEEDOR: 'Proveedor', EMPRESA: 'Empresa', SEDE: 'Sede', CENTRO_COSTO: 'Centro de costo', DELEGACION: 'Delegación',
  PRESUPUESTO: 'Presupuesto', CONTRATO: 'Contrato', POLITICA_APROBACION: 'Política de aprobación', CATEGORIA_PRODUCTO: 'Categoría',
  UNIDAD_MEDIDA: 'Unidad de medida', PRODUCTO: 'Producto', BODEGA: 'Bodega', MOVIMIENTO_INVENTARIO: 'Movimiento de inventario', DOCUMENTO: 'Documento',
  CONFIG_NOTIFICACION: 'Aviso o correo', SOLICITUD_COMPRA: 'Solicitud', RFQ: 'RFQ', DESPACHO: 'Despacho', COTIZACION: 'Cotización',
  CONFIG_PESOS: 'Pesos de evaluación', ANALISIS: 'Análisis', ADJUDICACION: 'Adjudicación', ORDEN_COMPRA: 'Orden de compra', PAGO: 'Pago',
  RECEPCION: 'Recepción', FACTURA: 'Factura', EXPORTACION: 'Exportación', INFORME_COMPRAS: 'Informe de compras',
  PEDIDO_INTERNO: 'Pedido interno', TARIFA_IMPUESTO: 'Impuesto de venta', LISTA_PRECIO: 'Lista de precios',
  PRECIO_PRODUCTO: 'Precio de venta', CATALOGO_ITEM: 'Artículo de catálogo', CLIENTE: 'Cliente',
  CAJA: 'Caja', TURNO_CAJA: 'Turno de caja', AUTORIZACION: 'Autorización de supervisor',
};

/** Pantalla donde se ve cada entidad, cuando existe una dirección por id. */
export const RUTA_ENTIDAD: Record<string, ((id: number) => string) | undefined> = {
  ORDEN_COMPRA: id => `/app/ordenes/${id}`,
  RFQ: id => `/app/cotizaciones/${id}`,
};

/** Códigos de entidad y de acción ordenados por su texto, para los selectores. */
export const LISTA_ENTIDADES = Object.keys(ENTIDADES).sort((a, b) => ENTIDADES[a]!.localeCompare(ENTIDADES[b]!, 'es'));
export const LISTA_ACCIONES = Object.keys(ACCIONES).sort((a, b) => ACCIONES[a]!.localeCompare(ACCIONES[b]!, 'es'));
