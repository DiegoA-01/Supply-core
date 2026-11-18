import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Pagina, RegistroAuditoria } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Filtros de la auditoría de la empresa; los vacíos no se envían. */
export interface FiltroAuditoria {
  usuarioId?: number | null;
  entidad?: string | null;
  entidadId?: number | null;
  accion?: string | null;
  resultado?: string | null;
  desde?: string | null;
  hasta?: string | null;
}

/** Auditoría de la empresa (solo lectura): quién hizo qué, cuándo y desde dónde. Nada se edita ni se borra. */
@Injectable({ providedIn: 'root' })
export class AuditoriaService {
  private readonly http = inject(HttpClient);

  /**
   * @param f      filtros
   * @param pagina número de página desde 0 (25 por página, lo más reciente primero)
   */
  buscar(f: FiltroAuditoria, pagina: number): Observable<Pagina<RegistroAuditoria>> {
    return this.http.get<Pagina<RegistroAuditoria>>(api('/api/auditoria'), { params: parametros({ ...f, page: pagina, size: 25, sort: 'creadoEn,desc' }) });
  }

  /** @param id registro de auditoría */
  obtener(id: number): Observable<RegistroAuditoria> {
    return this.http.get<RegistroAuditoria>(api(`/api/auditoria/${id}`));
  }
}
