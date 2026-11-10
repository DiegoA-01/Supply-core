import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Adjudicacion, Analisis, Bodega, Cotizacion, Orden, Pagina, Pesos, Rfq, RfqResumen } from '../api/modelos';
import { api, parametros } from '../http/api';

/** RFQ, análisis con IA, adjudicación y emisión de la orden. */
@Injectable({ providedIn: 'root' })
export class CotizacionesService {
  private readonly http = inject(HttpClient);

  /** @param filtros estado y texto del consecutivo */
  rfqs(filtros: { estado?: string | null; texto?: string | null }): Observable<Pagina<RfqResumen>> {
    return this.http.get<Pagina<RfqResumen>>(api('/api/rfqs'),
      { params: parametros({ estado: filtros.estado, texto: filtros.texto, size: 50, sort: 'creadoEn,desc' }) });
  }

  /** @param id RFQ */
  rfq(id: number): Observable<Rfq> {
    return this.http.get<Rfq>(api(`/api/rfqs/${id}`));
  }

  /** @param rfqId RFQ */
  cotizaciones(rfqId: number): Observable<Cotizacion[]> {
    return this.http.get<Cotizacion[]>(api(`/api/rfqs/${rfqId}/cotizaciones`));
  }

  /** Análisis vigente (404 si aún no se generó). */
  analisis(rfqId: number): Observable<Analisis> {
    return this.http.get<Analisis>(api(`/api/rfqs/${rfqId}/analisis`));
  }

  /** Genera (o regenera en borrador) el análisis: Java calcula los puntajes. */
  generarAnalisis(rfqId: number): Observable<Analisis> {
    return this.http.post<Analisis>(api(`/api/rfqs/${rfqId}/analisis`), {});
  }

  /** Pide a la IA que explique el ranking ya calculado. */
  explicar(rfqId: number): Observable<Analisis> {
    return this.http.post<Analisis>(api(`/api/rfqs/${rfqId}/analisis/explicar`), {});
  }

  /** Publica el análisis (falla si quedan datos por confirmar). */
  publicar(rfqId: number): Observable<Analisis> {
    return this.http.post<Analisis>(api(`/api/rfqs/${rfqId}/analisis/publicar`), {});
  }

  /** Adjudicación de la RFQ (404 si no hay). */
  adjudicacion(rfqId: number): Observable<Adjudicacion> {
    return this.http.get<Adjudicacion>(api(`/api/rfqs/${rfqId}/adjudicacion`));
  }

  /**
   * Adjudica una cotización del ranking.
   *
   * @param rfqId         RFQ
   * @param cotizacionId  cotización elegida
   * @param justificacion obligatoria si no es la recomendada
   */
  adjudicar(rfqId: number, cotizacionId: number, justificacion: string): Observable<Adjudicacion> {
    return this.http.post<Adjudicacion>(api(`/api/rfqs/${rfqId}/adjudicacion`), { cotizacionId, justificacion });
  }

  /** Pesos vigentes de los criterios. */
  pesos(): Observable<Pesos> {
    return this.http.get<Pesos>(api('/api/config-pesos'));
  }

  /** Bodegas activas (destino de la orden). */
  bodegas(): Observable<Bodega[]> {
    return this.http.get<Bodega[]>(api('/api/bodegas'), { params: parametros({ activo: true }) });
  }

  /**
   * Emite la orden de una adjudicación aprobada.
   *
   * @param datos adjudicación, bodega, lugar y fecha de entrega
   */
  emitirOrden(datos: { adjudicacionId: number; bodegaDestinoId: number; lugarEntrega: string; fechaEntregaEsperada: string }): Observable<Orden> {
    return this.http.post<Orden>(api('/api/ordenes'), datos);
  }
}
