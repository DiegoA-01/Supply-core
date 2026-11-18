import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Despacho, DespachoNuevo, DespachoResumen, Pagina } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Despachos: salidas de mercancía a clientes o consumo, que descuentan el stock al confirmarse. */
@Injectable({ providedIn: 'root' })
export class DespachosService {
  private readonly http = inject(HttpClient);

  /**
   * @param f      texto (consecutivo, cliente o referencia), estado y bodega
   * @param pagina número de página desde 0
   */
  buscar(f: { texto?: string | null; estado?: string | null; bodegaId?: number | null }, pagina: number): Observable<Pagina<DespachoResumen>> {
    return this.http.get<Pagina<DespachoResumen>>(api('/api/despachos'), { params: parametros({ ...f, page: pagina, size: 20, sort: 'creadoEn,desc' }) });
  }

  /** @param id despacho; detalle con líneas */
  obtener(id: number): Observable<Despacho> {
    return this.http.get<Despacho>(api(`/api/despachos/${id}`));
  }

  /** @param datos bodega, cliente, referencia y líneas; queda en borrador y no toca el stock */
  crear(datos: DespachoNuevo): Observable<Despacho> {
    return this.http.post<Despacho>(api('/api/despachos'), datos);
  }

  /** @param id despacho en borrador; @param datos nuevo contenido */
  actualizar(id: number, datos: DespachoNuevo): Observable<Despacho> {
    return this.http.put<Despacho>(api(`/api/despachos/${id}`), datos);
  }

  /** @param id despacho en borrador; descuenta el stock y avisa qué productos quedaron bajo su mínimo */
  confirmar(id: number): Observable<Despacho> {
    return this.http.post<Despacho>(api(`/api/despachos/${id}/confirmar`), {});
  }

  /** @param id despacho; @param motivo entre 5 y 500 caracteres; si estaba confirmado, devuelve el stock */
  anular(id: number, motivo: string): Observable<Despacho> {
    return this.http.post<Despacho>(api(`/api/despachos/${id}/anular`), { motivo });
  }
}
