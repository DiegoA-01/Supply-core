import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CargaPrecios, HistorialPrecio, ListaPrecio, PrecioFila, Precios, ProductoParaVender, TarifaImpuesto,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Precios de venta, impuestos y margen (fase F1 de Ventas). Todo cálculo de dinero lo hace el backend. */
@Injectable({ providedIn: 'root' })
export class VentasService {
  private readonly http = inject(HttpClient);

  /** Impuestos de venta de la empresa. */
  impuestos(): Observable<TarifaImpuesto[]> {
    return this.http.get<TarifaImpuesto[]>(api('/api/ventas/impuestos'));
  }

  /** @param codigo código corto; @param nombre nombre visible; @param porcentaje tarifa */
  crearImpuesto(codigo: string, nombre: string, porcentaje: number): Observable<TarifaImpuesto> {
    return this.http.post<TarifaImpuesto>(api('/api/ventas/impuestos'), { codigo, nombre, porcentaje });
  }

  /** Listas de precios (la General primero). */
  listas(): Observable<ListaPrecio[]> {
    return this.http.get<ListaPrecio[]>(api('/api/ventas/listas'));
  }

  /** @param nombre nombre de la lista nueva */
  crearLista(nombre: string): Observable<ListaPrecio> {
    return this.http.post<ListaPrecio>(api('/api/ventas/listas'), { nombre });
  }

  /** @param listaId lista (vacío = General); precios con margen y resumen */
  precios(listaId: number | null): Observable<Precios> {
    return this.http.get<Precios>(api('/api/ventas/precios'), { params: parametros({ listaId }) });
  }

  /**
   * Fija un precio nuevo (el anterior queda en el historial).
   *
   * @param listaId      lista
   * @param productoId   producto
   * @param precio       precio de venta
   * @param precioMinimo piso opcional
   * @param motivo       por qué cambia
   * @param unidadMedidaId unidad del precio (null = la del producto)
   */
  fijarPrecio(listaId: number, productoId: number, precio: number, precioMinimo: number | null, motivo: string | null,
    unidadMedidaId: number | null = null): Observable<PrecioFila> {
    return this.http.put<PrecioFila>(api('/api/ventas/precios'), { listaId, productoId, precio, precioMinimo, motivo, unidadMedidaId });
  }

  /** @param listaId lista; @param productoId producto; @param unidadMedidaId unidad (null = la del producto) */
  historial(listaId: number, productoId: number, unidadMedidaId: number | null = null): Observable<HistorialPrecio[]> {
    return this.http.get<HistorialPrecio[]>(api('/api/ventas/precios/historial'),
      { params: parametros({ listaId, productoId, unidadMedidaId }) });
  }

  /**
   * Cambio masivo de precios por Excel (todo o nada).
   *
   * @param archivo     Excel .xlsx con codigo, lista, unidad, precio, precio_minimo y motivo
   * @param soloValidar true para revisar sin guardar
   */
  cargarPrecios(archivo: File, soloValidar: boolean): Observable<CargaPrecios> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<CargaPrecios>(api('/api/ventas/precios/carga'), datos, { params: parametros({ soloValidar }) });
  }

  /** Excel de ejemplo para la carga de precios. */
  plantillaPrecios(): Observable<Blob> {
    return this.http.get(api('/api/ventas/precios/carga/plantilla'), { responseType: 'blob' });
  }

  /**
   * Busca productos para vender (sin costos ni márgenes).
   *
   * @param q        código, código de barras o nombre
   * @param listaId  lista (null = General)
   * @param bodegaId bodega del stock (null = todas)
   */
  buscarParaVender(q: string, listaId: number | null = null, bodegaId: number | null = null): Observable<ProductoParaVender[]> {
    return this.http.get<ProductoParaVender[]>(api('/api/ventas/productos'), { params: parametros({ q, listaId, bodegaId }) });
  }

  /**
   * Datos de venta del producto.
   *
   * @param productoId           producto
   * @param tarifaImpuestoId     impuesto
   * @param vendible             si se vende
   * @param precioIncluyeImpuesto si el precio trae el impuesto
   * @param codigoBarras         código de barras
   */
  configurarProducto(productoId: number, tarifaImpuestoId: number | null, vendible: boolean, precioIncluyeImpuesto: boolean,
    codigoBarras: string | null): Observable<PrecioFila> {
    return this.http.put<PrecioFila>(api(`/api/ventas/productos/${productoId}`), { tarifaImpuestoId, vendible, precioIncluyeImpuesto, codigoBarras });
  }
}
