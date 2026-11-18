import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Adjudicacion, Pagina, PoliticaAplicable } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Adjudicaciones pendientes de aprobación y la decisión del aprobador. */
@Injectable({ providedIn: 'root' })
export class AdjudicacionesService {
  private readonly http = inject(HttpClient);

  /** @param pagina número de página desde 0; las pendientes de mi rol (o de uno delegado), sin las que yo adjudiqué */
  pendientes(pagina: number): Observable<Pagina<Adjudicacion>> {
    return this.http.get<Pagina<Adjudicacion>>(api('/api/adjudicaciones/pendientes'), { params: parametros({ page: pagina, size: 20 }) });
  }

  /** @param id adjudicación */
  obtener(id: number): Observable<Adjudicacion> {
    return this.http.get<Adjudicacion>(api(`/api/adjudicaciones/${id}`));
  }

  /**
   * Aprueba o rechaza una adjudicación pendiente. Quien adjudicó no puede aprobar la suya (segregación de funciones).
   *
   * @param id            adjudicación pendiente
   * @param aprobar       {@code true} aprueba, {@code false} rechaza
   * @param observaciones motivo (obligatorio al rechazar)
   */
  decidir(id: number, aprobar: boolean, observaciones: string): Observable<Adjudicacion> {
    return this.http.post<Adjudicacion>(api(`/api/adjudicaciones/${id}/decision`), { aprobar, observaciones: observaciones || undefined });
  }

  /**
   * Qué política de aprobación aplica a un monto (qué rol aprueba).
   *
   * @param tipo  SOLICITUD o ADJUDICACION
   * @param monto monto total
   */
  politicaAplicable(tipo: 'SOLICITUD' | 'ADJUDICACION', monto: number): Observable<PoliticaAplicable> {
    return this.http.get<PoliticaAplicable>(api('/api/politicas-aprobacion/aplicable'), { params: parametros({ tipo, monto }) });
  }
}
