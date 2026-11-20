import { Component, Pipe, PipeTransform, computed, input } from '@angular/core';

/** Color del chip según el estado. */
export type Tono = 'verde' | 'ambar' | 'rojo' | 'naranja' | 'gris';

/**
 * Texto legible y color de cada estado o tipo que devuelve el backend ({@code RECIBIDA_TOTAL} → "Recibida total").
 * Solo cambia cómo se muestra; la API sigue usando los códigos.
 */
const ETIQUETAS: Record<string, [string, Tono]> = {
  // Solicitud
  BORRADOR: ['Borrador', 'gris'],
  PENDIENTE_APROBACION: ['Pendiente de aprobación', 'ambar'],
  APROBADA: ['Aprobada', 'verde'],
  DEVUELTA: ['Devuelta', 'ambar'],
  RECHAZADA: ['Rechazada', 'rojo'],
  EN_RFQ: ['En cotización', 'naranja'],
  ADJUDICADA: ['Adjudicada', 'verde'],
  CANCELADA: ['Cancelada', 'gris'],
  CERRADA: ['Cerrada', 'verde'],
  // RFQ y cotización
  ABIERTA: ['Abierta', 'verde'],
  ENVIADA: ['Enviada', 'verde'],
  EN_ANALISIS: ['En análisis', 'naranja'],
  SELECCIONADA: ['Seleccionada', 'verde'],
  NO_SELECCIONADA: ['No seleccionada', 'gris'],
  PUBLICADO: ['Publicado', 'verde'],
  // Orden
  EMITIDA: ['Emitida', 'gris'],
  ACEPTADA_PROVEEDOR: ['Aceptada por el proveedor', 'verde'],
  RECHAZADA_PROVEEDOR: ['Rechazada por el proveedor', 'rojo'],
  EN_PREPARACION: ['En preparación', 'naranja'],
  DESPACHADA: ['Despachada', 'naranja'],
  RECIBIDA_PARCIAL: ['Recibida parcial', 'ambar'],
  RECIBIDA_TOTAL: ['Recibida total', 'verde'],
  REEMPLAZADA: ['Reemplazada', 'gris'],
  // Factura y pago
  CUADRADA: ['Cuadrada', 'verde'],
  EN_REVISION: ['En revisión', 'ambar'],
  ANULADA: ['Anulada', 'gris'],
  ANULADO: ['Anulado', 'gris'],
  REGISTRADO: ['Registrado', 'verde'],
  PENDIENTE: ['Pendiente', 'ambar'],
  ANTICIPO: ['Anticipo', 'gris'],
  PAGO_FINAL: ['Pago final', 'gris'],
  // Recepción
  ACEPTADA: ['Aceptada', 'verde'],
  EXCEDENTE_DEVUELTO: ['Excedente devuelto', 'gris'],
  DANADO: ['Dañado', 'rojo'],
  NO_CUMPLE_ESPECIFICACION: ['No cumple especificación', 'rojo'],
  VENCIDO: ['Vencido', 'rojo'],
  CANTIDAD_ERRADA: ['Cantidad errada', 'rojo'],
  OTRO: ['Otro', 'gris'],
  // Proveedor y suscripción
  HABILITADO: ['Habilitado', 'verde'],
  RECHAZADO: ['Rechazado', 'rojo'],
  SUSPENDIDO: ['Suspendido', 'rojo'],
  PRUEBA: ['En prueba', 'gris'],
  ACTIVA: ['Activa', 'verde'],
  MOROSA: ['En mora', 'ambar'],
  SUSPENDIDA: ['Suspendida', 'rojo'],
  CONFIRMADO: ['Confirmado', 'verde'],
  REVOCADA: ['Revocada', 'gris'],
  // Movimientos de inventario
  ENTRADA_COMPRA: ['Entrada por compra', 'verde'],
  SALIDA_CONSUMO: ['Salida por consumo', 'ambar'],
  AJUSTE_POSITIVO: ['Ajuste que suma', 'verde'],
  AJUSTE_NEGATIVO: ['Ajuste que resta', 'rojo'],
  DEVOLUCION_PROVEEDOR: ['Devolución al proveedor', 'ambar'],
  TRASLADO: ['Traslado entre bodegas', 'naranja'],
  SALIDA_VENTA: ['Salida por despacho', 'ambar'],
  ANULACION_DESPACHO: ['Despacho anulado', 'gris'],
  ANULACION_VENTA: ['Venta anulada', 'gris'],
  DEVOLUCION_CLIENTE: ['Devolución de cliente', 'verde'],
  // Pago a proveedores
  TRANSFERENCIA: ['Transferencia', 'gris'],
  CONSIGNACION: ['Consignación', 'gris'],
  CHEQUE: ['Cheque', 'gris'],
  EFECTIVO: ['Efectivo', 'gris'],
  TARJETA: ['Tarjeta', 'gris'],
};

/** Roles con nombre para mostrar. */
const ROLES: Record<string, string> = {
  ADMIN_SISTEMA: 'Administrador',
  ADMIN_COMPRAS: 'Jefe de compras',
  SOLICITANTE: 'Solicitante',
  COMPRADOR: 'Comprador',
  APROBADOR: 'Aprobador',
  ALMACENISTA: 'Bodega',
  CAJERO: 'Caja',
  SUPERVISOR_VENTAS: 'Supervisor de ventas',
  VENDEDOR: 'Vendedor',
  ADMINISTRATIVO: 'Tesorería',
  AUDITOR: 'Auditoría',
  PROVEEDOR: 'Proveedor',
};

/**
 * Texto de un estado; uno sin texto definido sale con mayúscula inicial y espacios.
 *
 * @param codigo código del backend
 */
export function etiqueta(codigo: string | null | undefined): string {
  if (!codigo) {
    return '—';
  }
  const t = ETIQUETAS[codigo]?.[0];
  if (t) {
    return t;
  }
  const s = codigo.replace(/_/g, ' ').toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** @param codigo código del backend */
export function tono(codigo: string | null | undefined): Tono {
  return (codigo && ETIQUETAS[codigo]?.[1]) || 'gris';
}

/** @param rol código del rol */
export function nombreRol(rol: string | null | undefined): string {
  return rol ? (ROLES[rol] ?? etiqueta(rol)) : '';
}

@Pipe({ name: 'etiqueta' })
export class EtiquetaPipe implements PipeTransform {
  /** @param codigo estado o tipo del backend */
  transform(codigo: string | null | undefined): string {
    return etiqueta(codigo);
  }
}

/** Chip de color con el texto legible de un estado. */
@Component({
  selector: 'sc-estado',
  template: `<span class="chip" [class]="'chip ' + clase()">{{ texto() }}</span>`,
})
export class Estado {
  /** Código del estado. */
  readonly valor = input<string | null | undefined>();
  /** Texto legible del estado. */
  protected readonly texto = computed(() => etiqueta(this.valor()));
  protected readonly clase = computed(() => tono(this.valor()));
}
