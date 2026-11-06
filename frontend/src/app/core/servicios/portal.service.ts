import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Cotizacion, CotizacionNueva, Factura, Orden, OrdenResumen, Pagina, PagosOrden, Proveedor, RfqPortal, RfqResumen,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Portal del proveedor: solo ve sus invitaciones, sus cotizaciones y sus órdenes. */
@Injectable({ providedIn: 'root' })
export class PortalService {
  private readonly http = inject(HttpClient);

  /** Datos del proveedor de la sesión. */
  proveedor(): Observable<Proveedor> {
    return this.http.get<Proveedor>(api('/api/portal/proveedor'));
  }

  /** RFQ a las que fue invitado. */
  rfqs(): Observable<Pagina<RfqResumen>> {
    return this.http.get<Pagina<RfqResumen>>(api('/api/portal/rfqs'), { params: parametros({ size: 50 }) });
  }

  /** @param id RFQ */
  rfq(id: number): Observable<RfqPortal> {
    return this.http.get<RfqPortal>(api(`/api/portal/rfqs/${id}`));
  }

  /** Su cotización para la RFQ (404 si aún no empezó). */
  cotizacion(rfqId: number): Observable<Cotizacion> {
    return this.http.get<Cotizacion>(api(`/api/portal/rfqs/${rfqId}/cotizacion`));
  }

  /**
   * Guarda el borrador; el backend calcula subtotal, IVA y total.
   *
   * @param rfqId RFQ
   * @param datos condiciones y precios por línea
   */
  guardar(rfqId: number, datos: CotizacionNueva): Observable<Cotizacion> {
    return this.http.put<Cotizacion>(api(`/api/portal/rfqs/${rfqId}/cotizacion`), datos);
  }

  /** Envía la cotización; después no se puede editar. */
  enviar(rfqId: number): Observable<Cotizacion> {
    return this.http.post<Cotizacion>(api(`/api/portal/rfqs/${rfqId}/cotizacion/enviar`), {});
  }

  /**
   * Sube la cotización en un archivo (Excel, PDF o imagen); la IA lee los datos.
   *
   * @param rfqId   RFQ
   * @param archivo archivo
   */
  subirArchivo(rfqId: number, archivo: File): Observable<Cotizacion> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<Cotizacion>(api(`/api/portal/rfqs/${rfqId}/cotizacion/archivo`), datos);
  }

  /** @param estado filtro opcional */
  ordenes(estado: string | null): Observable<Pagina<OrdenResumen>> {
    return this.http.get<Pagina<OrdenResumen>>(api('/api/portal/ordenes'), { params: parametros({ estado, size: 50, sort: 'creadoEn,desc' }) });
  }

  /** @param id orden */
  orden(id: number): Observable<Orden> {
    return this.http.get<Orden>(api(`/api/portal/ordenes/${id}`));
  }

  /**
   * Cambia el estado de su orden.
   *
   * @param id          orden
   * @param accion      aceptar, rechazar, preparar o despachar
   * @param observacion motivo del rechazo o guía del despacho
   */
  accion(id: number, accion: 'aceptar' | 'rechazar' | 'preparar' | 'despachar', observacion = ''): Observable<Orden> {
    return this.http.post<Orden>(api(`/api/portal/ordenes/${id}/${accion}`), accion === 'aceptar' || accion === 'preparar' ? {} : { observacion });
  }

  /** @param ordenId orden */
  pagos(ordenId: number): Observable<PagosOrden> {
    return this.http.get<PagosOrden>(api(`/api/portal/ordenes/${ordenId}/pagos`));
  }

  /** @param ordenId orden */
  facturas(ordenId: number): Observable<Factura[]> {
    return this.http.get<Factura[]>(api(`/api/portal/ordenes/${ordenId}/facturas`));
  }
}
