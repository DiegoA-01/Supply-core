import { components } from './esquema';

/**
 * Nombres cortos para los tipos que genera `npm run api:tipos` desde el Swagger del backend.
 * Si el backend cambia un DTO, `esquema.ts` cambia y el compilador marca aquí y en cada pantalla qué ajustar.
 */
type S = components['schemas'];

/** Página que devuelve el backend en los listados ({@code PageResponse}). */
export interface Pagina<T> {
  contenido: T[];
  pagina: number;
  tamano: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Error que devuelve el backend ({@code ErrorResponse}). */
export interface ErrorApi {
  codigo: string;
  mensaje: string;
  detalles?: string[];
}

// Sesión y empresa
export type LoginResponse = S['LoginResponse'];
export type UsuarioSesion = S['UsuarioSesionResponse'];
export type Usuario = S['UsuarioResponse'];
export type UsuarioNuevo = S['UsuarioRequest'];
export type UsuarioCambios = S['UsuarioUpdateRequest'];
export type Rol = S['RolResponse'];
export type Empresa = S['EmpresaResponse'];
export type EmpresaCambios = S['EmpresaRequest'];
export type ModulosEmpresa = S['ModulosEmpresaResponse'];
export type Notificacion = S['NotificacionResponse'];
export type CentroCosto = S['CentroCostoResponse'];
export type Categoria = S['CategoriaResponse'];
export type UnidadMedida = S['UnidadMedidaResponse'];
export type Bodega = S['BodegaResponse'];

// Compras
export type SolicitudResumen = S['SolicitudResumenResponse'];
export type Solicitud = S['SolicitudResponse'];
export type Presupuesto = S['PresupuestoResponse'];
export type Politica = S['PoliticaAprobacionResponse'];
export type SolicitudNueva = S['SolicitudRequest'];
export type RfqDatos = S['RfqDatosRequest'];
export type DecisionSolicitud = NonNullable<S['DecisionRequest']['decision']>;
export type RfqResumen = S['RfqResumenResponse'];
export type Rfq = S['RfqResponse'];
export type Analisis = S['AnalisisResponse'];
export type ResultadoAnalisis = S['Resultado'];
export type Cotizacion = S['CotizacionResponse'];
export type Adjudicacion = S['AdjudicacionResponse'];
export type Pesos = S['PesosResponse'];
export type Procesamiento = S['ProcesamientoResponse'];
export type CampoLeido = S['Campo'];
export type LineaDigitada = S['LineaCotizacionRequest'];
export type LineaCotizacion = S['CotizacionLinea'];
export type CorreoExportacion = S['CorreoExportacionResponse'];
export type ConfiguracionMfa = S['ConfiguracionMfaResponse'];
export type MensajeApi = S['MensajeResponse'];
export type ConfigNotificacion = S['ConfigNotificacionResponse'];
export type StockBodega = S['StockResponse'];
export type Rotacion = S['RotacionResponse'];
export type ItemRotacion = S['RotacionItem'];
export type ClaseRotacion = NonNullable<ItemRotacion['clase']>;
export type Despacho = S['DespachoResponse'];
export type DespachoLinea = S['DespachoLinea'];
export type DespachoResumen = S['DespachoResumenResponse'];
export type DespachoNuevo = S['DespachoRequest'];
export type LineaDespachoNueva = S['LineaDespachoRequest'];
export type PoliticaAplicable = S['PoliticaAplicableResponse'];
export type OfertaContrato = S['OfertaContratoResponse'];
export type OfertaContratoLinea = S['OfertaContratoLinea'];
export type EmitirDirecta = S['EmitirDirectaRequest'];
export type NuevaVersionOrden = S['NuevaVersionRequest'];
export type Delegacion = S['DelegacionResponse'];
export type DelegacionNueva = S['DelegacionRequest'];
export type DelegadoCandidato = S['DelegadoCandidato'];
export type PoliticaNueva = S['PoliticaAprobacionRequest'];
export type PresupuestoNuevo = S['PresupuestoRequest'];
export type Contrato = S['ContratoResponse'];
export type ContratoResumen = S['ContratoResumenResponse'];
export type ContratoNuevo = S['ContratoRequest'];
export type PrecioContrato = S['Precio'];
export type PrecioContratoNuevo = S['PrecioRequest'];
export type Evaluacion = S['EvaluacionResponse'];
export type ProveedorCambios = S['ActualizarProveedorRequest'];
export type PesoCriterio = S['Peso'];
export type RegistroAuditoria = S['AuditoriaResponse'];
export type RegistroProveedor = S['RegistroProveedorRequest'];
export type RegistroProveedorRespuesta = S['RegistroProveedorResponse'];
export type Movimiento = S['MovimientoResponse'];
export type MovimientoNuevo = S['MovimientoRequest'];
export type RegistroMovimiento = S['RegistroMovimientoResponse'];
export type TipoMovimiento = MovimientoNuevo['tipo'];

// Órdenes, facturas, pagos y recepción
export type OrdenResumen = S['OrdenResumenResponse'];
export type Orden = S['OrdenResponse'];
export type OrdenLinea = S['OrdenLinea'];
export type Factura = S['FacturaResponse'];
export type FacturaNueva = S['RegistrarFacturaRequest'];
export type PagosOrden = S['PagosOrdenResponse'];
export type Pago = S['PagoResponse'];
export type RegistrarPago = S['RegistrarPagoRequest'];
export type ResumenRecepcion = S['ResumenRecepcionResponse'];
export type AvanceRecepcion = S['Avance'];
export type Recepcion = S['RecepcionResponse'];
export type LineaRecepcion = S['LineaRecepcionResponse'];
export type RegistrarRecepcion = S['RegistrarRecepcionRequest'];
export type MotivoRechazo = NonNullable<S['LineaRecepcionRequest']['motivoRechazo']>;

// Inventario y reportes
export type Producto = S['ProductoResponse'];
export type ProductoNuevo = S['ProductoRequest'];
export type InventarioResumen = S['InventarioResumen'];
export type InventarioItem = S['InventarioItem'];
export type SugerenciaReorden = S['SugerenciaReorden'];
export type Reabastecimiento = S['ReabastecimientoResponse'];
export type ItemReabastecimiento = S['ReabastecimientoItem'];
export type EstadoReabastecimiento = NonNullable<ItemReabastecimiento['estado']>;
export type Tablero = S['TableroResponse'];
export type ResumenSede = S['ResumenSede'];
export type PedidoInterno = S['PedidoInternoResponse'];
export type EstadoPedidoInterno = NonNullable<PedidoInterno['estado']>;
export type Precios = S['PreciosResponse'];
export type PrecioFila = S['PrecioFila'];
export type ListaPrecio = S['ListaPrecioResponse'];
export type TarifaImpuesto = S['TarifaImpuestoResponse'];
export type HistorialPrecio = S['HistorialPrecio'];
export type PrecioUnidad = S['PrecioUnidad'];
export type CargaPrecios = S['CargaPreciosResponse'];
export type ProductoParaVender = S['ProductoParaVender'];
export type Cliente = S['ClienteResponse'];
export type ClienteRequest = S['ClienteRequest'];
export type TipoDocumento = ClienteRequest['tipoDocumento'];
export type Caja = S['CajaResponse'];
export type CajaRequest = S['CajaRequest'];
export type ConfiguracionCaja = S['ConfiguracionCaja'];
export type Turno = S['TurnoResponse'];
export type EstadoTurno = NonNullable<Turno['estado']>;
export type MovimientoCaja = S['MovimientoCajaResponse'];
export type MedioPago = NonNullable<MovimientoCaja['medioPago']>;
export type AccionAutorizada = 'ANULAR_VENTA' | 'DESCUENTO' | 'PRECIO_BAJO_MINIMO' | 'DEVOLUCION' | 'RETIRO' | 'GASTO' | 'INGRESO';
export type Autorizacion = S['AutorizacionResponse'];
export type LineaVentaRequest = S['LineaVentaRequest'];
export type PagoVentaRequest = S['PagoVentaRequest'];
export type MedioPagoVenta = PagoVentaRequest['medio'];
export type VentaRequest = S['VentaRequest'];
export type Venta = S['VentaResponse'];
export type EstadoVenta = NonNullable<Venta['estado']>;
export type VentaResumen = S['VentaResumen'];
export type CotizacionVenta = S['CotizacionVenta'];
export type LineaCotizada = S['LineaCotizada'];
export type Tiquete = S['TiqueteResponse'];
export type PedidoVenta = S['PedidoVentaResponse'];
export type PedidoVentaRequest = S['PedidoVentaRequest'];
export type EstadoPedidoVenta = NonNullable<PedidoVenta['estado']>;
export type LimiteDescuento = S['LimiteDescuento'];
export type DevolucionRequest = S['DevolucionRequest'];
export type Devolucion = S['DevolucionResponse'];
export type FormaReembolso = DevolucionRequest['formaReembolso'];
export type EstadoProductoDevuelto = NonNullable<S['DevolucionLineaRequest']['estadoProducto']>;
export type ResumenCartera = S['ResumenCartera'];
export type ClienteCartera = S['ClienteCartera'];
export type VentaReporte = S['VentaReporte'];
export type FilaVentaReporte = S['FilaVentaReporte'];
export type AgrupacionVenta = NonNullable<S['VentaReporte']['agrupar']>;
export type CajaReporte = S['CajaReporte'];
export type Cuadre = S['CuadreResponse'];
export type TableroVentas = S['TableroVentas'];
export type DetalleCartera = S['DetalleCartera'];
export type CuentaPorCobrar = S['CuentaResponse'];
export type Abono = S['AbonoResponse'];
export type CatalogoItem = S['CatalogoItemResponse'];
export type EstadoEquivalencia = NonNullable<CatalogoItem['estadoEquivalencia']>;
export type PortalCatalogoItem = S['PortalCatalogoItem'];
export type CargaCatalogo = S['CargaCatalogoResponse'];
export type Oportunidad = S['Oportunidad'];
export type ProductoEnSede = S['ProductoSedeResponse'];
export type CargaMasiva = S['CargaMasivaResponse'];
export type Gasto = S['GastoResponse'];
export type GastoItem = S['GastoItem'];
export type ProveedorRanking = S['ProveedorRanking'];
export type EstadoCuenta = S['EstadoCuentaResponse'];
export type Proveedor = S['ProveedorResponse'];

// Plataforma
export type EmpresaPlataforma = S['EmpresaPlataformaResponse'];
export type EmpresaDetalle = S['EmpresaDetalleResponse'];
export type EmpresaCrear = S['EmpresaCrearRequest'];
export type DesafioMfa = S['DesafioMfaResponse'];
export type SesionPlataforma = S['SesionPlataformaResponse'];
export type Plan = S['Respuesta'];
export type PlanNuevo = S['Crear'];
export type PlanCambios = S['Editar'];
export type ModuloPlataforma = S['ModuloRespuesta'];
export type AuditoriaPlataforma = S['AuditoriaPlataformaResponse'];
export type EstadoSuscripcion = NonNullable<S['ModulosEmpresaResponse']['estado']>;

// Portal del proveedor
export type RfqPortal = S['RfqPortalResponse'];
export type CotizacionNueva = S['CotizacionRequest'];

// ------------------------------------------------------------ informe de compras (V25)
/** Cifras del periodo del informe de compras (las suma el backend). */
export interface InformeResumen {
  solicitudes: number; montoSolicitado: number; solicitudesPorEstado: Record<string, number>;
  rfqs: number; cotizacionesRecibidas: number; adjudicaciones: number; adjudicacionesFueraDeRecomendacion: number; montoAdjudicado: number;
}
/** Producto pedido y su cuenta: cantidad (en la unidad del producto) x costo de referencia, calculada por el backend. */
export interface InformeLinea { codigo: string; producto: string; cantidad: number; unidad: string; cantidadEnUnidadProducto?: number | null; unidadProducto: string; costoReferencia?: number | null; subtotal?: number | null; }
export interface InformeSolicitud {
  consecutivo: string; solicitante: string; centroCosto: string; estado: string; montoEstimado?: number | null; montoIncompleto: boolean; motivo?: string | null; enviadoEn?: string;
  /** Vienen desde que el informe trae el detalle; los informes enviados antes no los tienen. */
  lineas?: InformeLinea[] | null; montoCalculado?: number | null; cuadra?: boolean | null;
}
export interface InformeRfq { consecutivo: string; solicitud: string; estado: string; invitados: number; cotizaciones: number; fechaLimite?: string; }
export interface InformeCotizacion { rfq: string; proveedor: string; estado: string; total?: number | null; plazoEntregaDias?: number | null; enviadoEn?: string; }
export interface InformeAdjudicacion { rfq: string; proveedor: string; montoTotal: number; sigueRecomendacion: boolean; justificacion?: string | null; estado: string; decididoEn?: string; }
/** Foto fija del periodo que arma el backend. */
export interface InformeContenido {
  desde: string; hasta: string; generadoEn: string; resumen: InformeResumen;
  solicitudes: InformeSolicitud[]; rfqs: InformeRfq[]; cotizaciones: InformeCotizacion[]; adjudicaciones: InformeAdjudicacion[];
  gasto: Gasto; recortado: boolean;
}
/** Informe de compras: lo pide el dueño (PEDIDO) y lo envía compras (ENVIADO). */
export interface InformeCompras {
  id: number; estado: 'PEDIDO' | 'ENVIADO'; desde: string; hasta: string; mensajePedido?: string | null;
  pedidoPor?: string | null; pedidoEn?: string | null; enviadoPor?: string | null; enviadoEn?: string | null; leidoEn?: string | null;
  analisis?: string | null; contenido?: InformeContenido | null;
}
