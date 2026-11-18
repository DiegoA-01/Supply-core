import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { InformeCompras, InformeContenido, Pagina } from '../api/modelos';
import { api, parametros } from '../http/api';

/**
 * Informe de compras entre el jefe de compras y el dueño de la empresa. El backend arma y suma todo; al enviarse
 * queda como foto fija del periodo.
 */
@Injectable({ providedIn: 'root' })
export class InformesService {
  private readonly http = inject(HttpClient);

  /** @param pagina número de página desde 0 */
  listar(pagina = 0): Observable<Pagina<InformeCompras>> {
    return this.http.get<Pagina<InformeCompras>>(api('/api/informes-compras'), { params: parametros({ page: pagina, size: 20 }) });
  }

  /** @param id informe; trae el análisis y la foto fija */
  obtener(id: number): Observable<InformeCompras> {
    return this.http.get<InformeCompras>(api(`/api/informes-compras/${id}`));
  }

  /** Lo que saldría hoy en el informe del periodo, sin guardarlo (solo compras). */
  vistaPrevia(desde: string, hasta: string): Observable<InformeContenido> {
    return this.http.get<InformeContenido>(api('/api/informes-compras/vista-previa'), { params: parametros({ desde, hasta }) });
  }

  /** El dueño pide un informe; a compras le llega el aviso. */
  pedir(desde: string, hasta: string, mensaje: string): Observable<InformeCompras> {
    return this.http.post<InformeCompras>(api('/api/informes-compras/pedir'), { desde, hasta, mensaje: mensaje || undefined });
  }

  /** Compras envía el informe con su análisis; {@code pedidoId} si responde un pedido. */
  enviar(desde: string, hasta: string, analisis: string, pedidoId: number | null): Observable<InformeCompras> {
    return this.http.post<InformeCompras>(api('/api/informes-compras'), { desde, hasta, analisis, pedidoId: pedidoId ?? undefined });
  }

  /** @param id informe enviado que el dueño abrió */
  marcarLeido(id: number): Observable<InformeCompras> {
    return this.http.post<InformeCompras>(api(`/api/informes-compras/${id}/leido`), {});
  }
}
