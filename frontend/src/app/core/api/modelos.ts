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
export type CargaMasiva = S['CargaMasivaResponse'];
export type Gasto = S['GastoResponse'];
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
