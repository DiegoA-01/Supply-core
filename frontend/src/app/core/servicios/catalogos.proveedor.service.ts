import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CargaCatalogo, CatalogoItem, EstadoEquivalencia, Oportunidad, Pagina, PortalCatalogoItem } from '../api/modelos';
import { api, parametros } from '../http/api';

/**
 * Catálogos de proveedores. El portal sube y ve su propio catálogo; compras ve todos, confirma equivalencias y revisa
 * oportunidades. Los precios comparables y el ahorro los calcula el backend.
 */
@Injectable({ providedIn: 'root' })
export class CatalogosProveedorService {
  private readonly http = inject(HttpClient);

  /** Portal: el catálogo del proveedor de la sesión. */
  miCatalogo(): Observable<PortalCatalogoItem[]> {
    return this.http.get<PortalCatalogoItem[]>(api('/api/portal/catalogo'));
  }

  /** @param archivo Excel .xlsx del catálogo; portal: sube o reemplaza el catálogo */
  subir(archivo: File): Observable<CargaCatalogo> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<CargaCatalogo>(api('/api/portal/catalogo'), datos);
  }

  /**
   * Compras: catálogo de todos los proveedores.
   *
   * @param proveedorId proveedor (vacío = todos)
   * @param estado      estado de la equivalencia (vacío = todos)
   * @param texto       búsqueda
   * @param pagina      página desde 0
   */
  buscar(proveedorId: number | null, estado: EstadoEquivalencia | null, texto: string, pagina = 0): Observable<Pagina<CatalogoItem>> {
    return this.http.get<Pagina<CatalogoItem>>(api('/api/catalogos'), { params: parametros({ proveedorId, estado, texto, page: pagina, size: 30 }) });
  }

  /**
   * Compras: confirma la equivalencia con un producto o descarta el artículo.
   *
   * @param id         artículo
   * @param estado     CONFIRMADA o DESCARTADA
   * @param productoId producto (al confirmar)
   */
  decidir(id: number, estado: EstadoEquivalencia, productoId: number | null): Observable<CatalogoItem> {
    return this.http.patch<CatalogoItem>(api(`/api/catalogos/${id}`), { estado, productoId });
  }

  /** Compras: oportunidades de compra. */
  oportunidades(): Observable<Oportunidad[]> {
    return this.http.get<Oportunidad[]>(api('/api/catalogos/oportunidades'));
  }

  /** @param productoId producto; ofertas confirmadas en los catálogos */
  ofertasDe(productoId: number): Observable<CatalogoItem[]> {
    return this.http.get<CatalogoItem[]>(api(`/api/catalogos/producto/${productoId}`));
  }
}
