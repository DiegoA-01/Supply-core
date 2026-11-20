import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CotizacionVenta, Devolucion, DevolucionRequest, EstadoPedidoVenta, EstadoVenta, LimiteDescuento, LineaVentaRequest, PagoVentaRequest, Pagina, PedidoVenta,
  PedidoVentaRequest, Tiquete, Venta, VentaRequest, VentaResumen,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Filtros del historial de ventas. */
export interface FiltroVentas {
  desde?: string | null;
  hasta?: string | null;
  cajaId?: number | null;
  estado?: EstadoVenta | null;
  consecutivo?: string | null;
  pagina?: number;
}

/**
 * La venta (specs 004 y 005): cotizar el carrito, confirmar con clave de idempotencia, historial, tiquete, pedidos de
 * venta y límites de descuento. Todos los montos los calcula el backend; aquí solo se envían y se muestran.
 */
@Injectable({ providedIn: 'root' })
export class VentaService {
  private readonly http = inject(HttpClient);

  /**
   * @param clienteId cliente (null = consumidor final)
   * @param lineas    carrito
   * @param pagos     pagos escritos (vacío = solo totales)
   */
  cotizar(clienteId: number | null, lineas: LineaVentaRequest[], pagos: PagoVentaRequest[] = []): Observable<CotizacionVenta> {
    return this.http.post<CotizacionVenta>(api('/api/ventas/cotizar'), { clienteId, lineas, pagos });
  }

  /**
   * @param datos venta
   * @param clave clave de idempotencia del carrito (se reenvía igual si falla la red)
   */
  confirmar(datos: VentaRequest, clave: string): Observable<Venta> {
    return this.http.post<Venta>(api('/api/ventas'), datos, { headers: new HttpHeaders({ 'Idempotency-Key': clave }) });
  }

  /** @param f filtros del historial */
  buscar(f: FiltroVentas): Observable<Pagina<VentaResumen>> {
    return this.http.get<Pagina<VentaResumen>>(api('/api/ventas'), {
      params: parametros({ desde: f.desde, hasta: f.hasta, cajaId: f.cajaId, estado: f.estado, consecutivo: f.consecutivo, page: f.pagina ?? 0, size: 20 }),
    });
  }

  /** @param id venta */
  obtener(id: number): Observable<Venta> {
    return this.http.get<Venta>(api(`/api/ventas/${id}`));
  }

  /** @param id venta; la segunda vez y siguientes el tiquete dice COPIA */
  tiquete(id: number): Observable<Tiquete> {
    return this.http.get<Tiquete>(api(`/api/ventas/${id}/tiquete`));
  }

  /**
   * Anula una venta del turno abierto (spec 006).
   *
   * @param id           venta
   * @param motivo       por qué se anula
   * @param autorizacion permiso de un solo uso del supervisor
   */
  anular(id: number, motivo: string, autorizacion: string): Observable<Venta> {
    return this.http.post<Venta>(api(`/api/ventas/${id}/anular`), { motivo, autorizacion });
  }

  /** @param datos devolución de cliente con su autorización */
  devolver(datos: DevolucionRequest): Observable<Devolucion> {
    return this.http.post<Devolucion>(api('/api/devoluciones'), datos);
  }

  /** @param ventaId venta; sus devoluciones */
  devoluciones(ventaId: number): Observable<Pagina<Devolucion>> {
    return this.http.get<Pagina<Devolucion>>(api('/api/devoluciones'), { params: parametros({ ventaId, size: 50 }) });
  }

  /** Límite de descuento sin autorización de cada rol. */
  limites(): Observable<LimiteDescuento[]> {
    return this.http.get<LimiteDescuento[]>(api('/api/ventas/limites-descuento'));
  }

  /** @param limites límites por rol */
  guardarLimites(limites: LimiteDescuento[]): Observable<LimiteDescuento[]> {
    return this.http.put<LimiteDescuento[]>(api('/api/ventas/limites-descuento'), limites);
  }

  /** @param datos pedido de venta sin cobrar */
  crearPedido(datos: PedidoVentaRequest): Observable<PedidoVenta> {
    return this.http.post<PedidoVenta>(api('/api/pedidos-venta'), datos);
  }

  /** @param estado estado (null = todos); @param pagina página */
  pedidos(estado: EstadoPedidoVenta | null, pagina = 0): Observable<Pagina<PedidoVenta>> {
    return this.http.get<Pagina<PedidoVenta>>(api('/api/pedidos-venta'), { params: parametros({ estado, page: pagina, size: 20 }) });
  }

  /** @param id pedido */
  pedido(id: number): Observable<PedidoVenta> {
    return this.http.get<PedidoVenta>(api(`/api/pedidos-venta/${id}`));
  }

  /** @param id pedido; @param datos datos nuevos */
  editarPedido(id: number, datos: PedidoVentaRequest): Observable<PedidoVenta> {
    return this.http.put<PedidoVenta>(api(`/api/pedidos-venta/${id}`), datos);
  }

  /** @param id pedido */
  descartarPedido(id: number): Observable<PedidoVenta> {
    return this.http.post<PedidoVenta>(api(`/api/pedidos-venta/${id}/descartar`), {});
  }
}
