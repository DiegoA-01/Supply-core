import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Cliente, ClienteRequest, Pagina } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Clientes de la empresa (spec 002). La validación del documento y los permisos los hace el backend. */
@Injectable({ providedIn: 'root' })
export class ClientesService {
  private readonly http = inject(HttpClient);

  /**
   * @param q      documento (empieza por) o nombre (contiene)
   * @param activo filtro por estado (null = todos)
   * @param pagina página (desde 0)
   */
  buscar(q: string | null, activo: boolean | null = null, pagina = 0): Observable<Pagina<Cliente>> {
    return this.http.get<Pagina<Cliente>>(api('/api/clientes'), { params: parametros({ q, activo, page: pagina, size: 20 }) });
  }

  /** El consumidor final de la empresa (ventas sin datos). */
  consumidorFinal(): Observable<Cliente> {
    return this.http.get<Cliente>(api('/api/clientes/consumidor-final'));
  }

  /** @param datos cliente nuevo */
  crear(datos: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>(api('/api/clientes'), datos);
  }

  /** @param id cliente; @param datos datos nuevos (cupo, plazo y lista vacíos se conservan) */
  editar(id: number, datos: ClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(api(`/api/clientes/${id}`), datos);
  }

  /** Excel con los clientes (solo dueño y auditoría). */
  exportar(): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/clientes'), { params: parametros({ formato: 'xlsx' }), responseType: 'blob' });
  }
}
