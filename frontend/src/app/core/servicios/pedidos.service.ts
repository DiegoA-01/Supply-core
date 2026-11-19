import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EstadoPedidoInterno, Pagina, PedidoInterno } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Datos de un pedido interno nuevo. */
export interface PedidoNuevo {
  productoId: number | null;
  descripcion: string | null;
  cantidad: number | null;
  sedeId: number | null;
  nota: string | null;
}

/** Pedidos internos ("Necesito este producto"): solo los ve la empresa; nunca los proveedores. */
@Injectable({ providedIn: 'root' })
export class PedidosService {
  private readonly http = inject(HttpClient);

  /** @param datos qué hace falta; compras recibe el aviso */
  crear(datos: PedidoNuevo): Observable<PedidoInterno> {
    return this.http.post<PedidoInterno>(api('/api/pedidos-internos'), datos);
  }

  /**
   * @param mios   solo los propios
   * @param estado filtro por estado (vacío = todos)
   * @param pagina página (desde 0)
   */
  listar(mios: boolean, estado: EstadoPedidoInterno | null, pagina = 0): Observable<Pagina<PedidoInterno>> {
    return this.http.get<Pagina<PedidoInterno>>(api('/api/pedidos-internos'), { params: parametros({ mios, estado, page: pagina, size: 20 }) });
  }

  /**
   * Compras toma, atiende o descarta un pedido.
   *
   * @param id        pedido
   * @param estado    EN_GESTION, ATENDIDO o DESCARTADO
   * @param respuesta qué se hizo o por qué no
   */
  gestionar(id: number, estado: EstadoPedidoInterno, respuesta: string | null): Observable<PedidoInterno> {
    return this.http.patch<PedidoInterno>(api(`/api/pedidos-internos/${id}`), { estado, respuesta });
  }
}
