import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Factura, FacturaNueva, LineaRecepcion, Orden, OrdenResumen, Pagina, Pago, PagosOrden, Recepcion, RegistrarPago, RegistrarRecepcion,
  ResumenRecepcion,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Órdenes de compra, facturas con cruce de tres vías, pagos y recepciones. */
@Injectable({ providedIn: 'root' })
export class OrdenesService {
  private readonly http = inject(HttpClient);

  /** @param filtros estado y texto del consecutivo */
  buscar(filtros: { estado?: string | null; texto?: string | null; tamano?: number }): Observable<Pagina<OrdenResumen>> {
    return this.http.get<Pagina<OrdenResumen>>(api('/api/ordenes'), {
      params: parametros({ estado: filtros.estado, texto: filtros.texto, size: filtros.tamano ?? 50, sort: 'creadoEn,desc' }),
    });
  }

  /** @param id orden */
  obtener(id: number): Observable<Orden> {
    return this.http.get<Orden>(api(`/api/ordenes/${id}`));
  }

  /** @param ordenId orden */
  facturas(ordenId: number): Observable<Factura[]> {
    return this.http.get<Factura[]>(api(`/api/ordenes/${ordenId}/facturas`));
  }

  /**
   * Registra la factura del proveedor; el backend la cruza con lo recibido y la deja CUADRADA o EN_REVISION.
   *
   * @param ordenId orden con recepciones
   * @param factura número, fecha y montos tal como vienen impresos en la factura
   */
  registrarFactura(ordenId: number, factura: FacturaNueva): Observable<Factura> {
    return this.http.post<Factura>(api(`/api/ordenes/${ordenId}/facturas`), factura);
  }

  /**
   * Adjunta el archivo de la factura (PDF o imagen).
   *
   * @param facturaId factura registrada
   * @param archivo   archivo
   */
  archivoFactura(facturaId: number, archivo: File): Observable<Factura> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<Factura>(api(`/api/facturas/${facturaId}/archivo`), datos);
  }

  /** Facturas por estado (p. ej. EN_REVISION para la bandeja de compras). */
  facturasPorEstado(estado: string): Observable<Pagina<Factura>> {
    return this.http.get<Pagina<Factura>>(api('/api/facturas'), { params: parametros({ estado, size: 20 }) });
  }

  /**
   * Aprueba una factura en revisión (no puede hacerlo quien la registró).
   *
   * @param id     factura
   * @param motivo obligatorio
   */
  aprobarFactura(id: number, motivo: string): Observable<Factura> {
    return this.http.post<Factura>(api(`/api/facturas/${id}/aprobar`), { motivo });
  }

  /**
   * Anula una factura (luego el proveedor emite una nota crédito o una factura nueva).
   *
   * @param id     factura
   * @param motivo obligatorio
   */
  anularFactura(id: number, motivo: string): Observable<Factura> {
    return this.http.post<Factura>(api(`/api/facturas/${id}/anular`), { motivo });
  }

  /** Pagos de la orden con total, pagado y saldo calculados por el backend. */
  pagos(ordenId: number): Observable<PagosOrden> {
    return this.http.get<PagosOrden>(api(`/api/ordenes/${ordenId}/pagos`));
  }

  /**
   * Registra un anticipo o el pago final (solo registro: no hay pasarela).
   *
   * @param ordenId orden
   * @param pago    método, valor, fecha, referencia y tipo
   */
  registrarPago(ordenId: number, pago: RegistrarPago): Observable<Pago> {
    return this.http.post<Pago>(api(`/api/ordenes/${ordenId}/pagos`), pago);
  }

  /** Avance por línea y recepciones registradas. */
  recepciones(ordenId: number): Observable<ResumenRecepcion> {
    return this.http.get<ResumenRecepcion>(api(`/api/ordenes/${ordenId}/recepciones`));
  }

  /**
   * Registra una entrega. El backend reparte aceptado, rechazado, excedente y faltante y sube el stock.
   *
   * @param ordenId orden
   * @param datos   bodega, observaciones y líneas
   */
  registrarRecepcion(ordenId: number, datos: RegistrarRecepcion): Observable<Recepcion> {
    return this.http.post<Recepcion>(api(`/api/ordenes/${ordenId}/recepciones`), datos);
  }

  /**
   * Acepta al stock o devuelve un excedente en revisión.
   *
   * @param lineaId línea de recepción
   * @param decision ACEPTAR o DEVOLVER
   * @param motivo   motivo
   */
  resolverExceso(lineaId: number, decision: 'ACEPTAR' | 'DEVOLVER', motivo: string): Observable<LineaRecepcion> {
    return this.http.post<LineaRecepcion>(api(`/api/recepciones/lineas/${lineaId}/resolver-exceso`), { decision, motivo });
  }

  /**
   * Adjunta un archivo (evidencia) a una entidad.
   *
   * @param entidad   p. ej. RECEPCION
   * @param entidadId id
   * @param archivo   archivo elegido
   */
  adjuntar(entidad: string, entidadId: number, archivo: File): Observable<unknown> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post(api('/api/documentos'), datos, { params: parametros({ entidad, entidadId }) });
  }

  /** PDF de la orden (descarga). */
  pdf(ordenId: number): Observable<Blob> {
    return this.http.get(api(`/api/exportaciones/ordenes/${ordenId}`), { params: parametros({ formato: 'pdf' }), responseType: 'blob' });
  }
}
