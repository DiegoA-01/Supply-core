import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Adjudicacion, Analisis, Bodega, Cotizacion, LineaDigitada, Orden, Pagina, Pesos, Procesamiento, Rfq, RfqDatos, RfqResumen } from '../api/modelos';
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

  /**
   * Crea la RFQ en borrador a partir de una solicitud aprobada. Sin proveedores elegidos, el sistema invita a los
   * habilitados de las categorías de los productos.
   *
   * @param solicitudId solicitud APROBADA
   * @param datos       fecha límite, condiciones, proveedores y especificaciones
   */
  crearRfq(solicitudId: number, datos: RfqDatos): Observable<Rfq> {
    return this.http.post<Rfq>(api('/api/rfqs'), { solicitudId, datos });
  }

  /**
   * Abre la RFQ: se envían las invitaciones y la solicitud pasa a EN_RFQ.
   *
   * @param id RFQ en borrador
   */
  abrirRfq(id: number): Observable<Rfq> {
    return this.http.post<Rfq>(api(`/api/rfqs/${id}/abrir`), {});
  }

  /**
   * Cancela la RFQ en borrador o abierta; la solicitud vuelve a quedar aprobada.
   *
   * @param id RFQ
   */
  cancelarRfq(id: number): Observable<Rfq> {
    return this.http.post<Rfq>(api(`/api/rfqs/${id}/cancelar`), {});
  }

  /**
   * Cierra la RFQ antes de la fecha límite para analizar lo recibido.
   *
   * @param id RFQ abierta
   */
  cerrarRfq(id: number): Observable<Rfq> {
    return this.http.post<Rfq>(api(`/api/rfqs/${id}/cerrar`), {});
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

  /**
   * Estado de la lectura con IA de una cotización y los datos leídos con su confianza.
   *
   * @param cotizacionId cotización recibida como archivo
   */
  extraccion(cotizacionId: number): Observable<Procesamiento> {
    return this.http.get<Procesamiento>(api(`/api/cotizaciones/${cotizacionId}/extraccion`));
  }

  /**
   * Confirma o corrige un dato leído por la IA.
   *
   * @param cotizacionId cotización
   * @param campoId      dato leído
   * @param valor        valor correcto, como texto
   */
  confirmarCampo(cotizacionId: number, campoId: number, valor: string): Observable<Procesamiento> {
    return this.http.patch<Procesamiento>(api(`/api/cotizaciones/${cotizacionId}/extraccion/campos/${campoId}`), { valor });
  }

  /**
   * Reemplaza lo leído por la IA con los precios digitados a mano.
   *
   * @param cotizacionId cotización
   * @param lineas       una por línea de la RFQ
   */
  digitarLineas(cotizacionId: number, lineas: LineaDigitada[]): Observable<Procesamiento> {
    return this.http.put<Procesamiento>(api(`/api/cotizaciones/${cotizacionId}/extraccion/lineas`), { lineas });
  }

  /** @param cotizacionId cotización cuyo archivo se vuelve a leer con la IA */
  reintentarExtraccion(cotizacionId: number): Observable<Procesamiento> {
    return this.http.post<Procesamiento>(api(`/api/cotizaciones/${cotizacionId}/extraccion/reintentar`), {});
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
