import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CargaMasiva, Categoria, CorreoExportacion, InventarioResumen, Pagina, Producto, ProductoNuevo, Solicitud, SugerenciaReorden, UnidadMedida,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Productos, stock, reorden sugerida y carga masiva. */
@Injectable({ providedIn: 'root' })
export class InventarioService {
  private readonly http = inject(HttpClient);

  /**
   * Resumen del inventario con valor y estado de cada producto (reporte de solo lectura).
   *
   * @param categoriaId    filtro opcional
   * @param soloBajoMinimo solo los que están bajo el mínimo
   */
  resumen(categoriaId: number | null, soloBajoMinimo: boolean): Observable<InventarioResumen> {
    return this.http.get<InventarioResumen>(api('/api/reportes/inventario'), { params: parametros({ categoriaId, soloBajoMinimo }) });
  }

  /** @param filtros texto, categoría y estado */
  productos(filtros: { texto?: string | null; categoriaId?: number | null; tamano?: number }): Observable<Pagina<Producto>> {
    return this.http.get<Pagina<Producto>>(api('/api/productos'), {
      params: parametros({ texto: filtros.texto, categoriaId: filtros.categoriaId, activo: true, size: filtros.tamano ?? 100, sort: 'nombre,asc' }),
    });
  }

  /** Productos bajo su stock mínimo. */
  alertas(): Observable<Producto[]> {
    return this.http.get<Producto[]>(api('/api/productos/alertas-stock'));
  }

  /** @param producto datos del producto nuevo */
  crear(producto: ProductoNuevo): Observable<Producto> {
    return this.http.post<Producto>(api('/api/productos'), producto);
  }

  categorias(): Observable<Categoria[]> {
    return this.http.get<Categoria[]>(api('/api/categorias'), { params: parametros({ activo: true }) });
  }

  unidades(): Observable<UnidadMedida[]> {
    return this.http.get<UnidadMedida[]>(api('/api/unidades-medida'), { params: parametros({ activo: true }) });
  }

  /** Cuánto pedir por producto (lo calcula el backend: máximo − stock − por recibir − ya solicitado). */
  sugerencias(): Observable<SugerenciaReorden[]> {
    return this.http.get<SugerenciaReorden[]>(api('/api/reorden/sugerencias'));
  }

  /**
   * Deja un borrador de solicitud con las cantidades sugeridas; el solicitante decide si lo envía.
   *
   * @param fechaRequerida fecha requerida (ISO)
   * @param productoIds    productos elegidos (vacío = todos los sugeridos)
   */
  borradorReorden(fechaRequerida: string, productoIds: number[]): Observable<Solicitud> {
    return this.http.post<Solicitud>(api('/api/reorden/borrador'), { fechaRequerida, productoIds });
  }

  /**
   * Carga masiva por Excel: primero se valida y luego se aplica (todo o nada).
   *
   * @param archivo     .xlsx
   * @param soloValidar {@code true} para revisar sin guardar
   */
  cargaMasiva(archivo: File, soloValidar: boolean): Observable<CargaMasiva> {
    const datos = new FormData();
    datos.append('archivo', archivo);
    return this.http.post<CargaMasiva>(api('/api/productos/carga-masiva'), datos, { params: parametros({ soloValidar }) });
  }

  /** Plantilla de Excel para la carga masiva. */
  plantilla(): Observable<Blob> {
    return this.http.get(api('/api/productos/carga-masiva/plantilla'), { responseType: 'blob' });
  }

  /** Envía las existencias en Excel al correo del usuario de la sesión. */
  stockPorCorreo(): Observable<CorreoExportacion> {
    return this.http.post<CorreoExportacion>(api('/api/exportaciones/inventario/stock/correo'), {}, { params: parametros({ formato: 'xlsx' }) });
  }

  /** Existencias en Excel. */
  exportarStock(): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/inventario/stock'), { params: parametros({ formato: 'xlsx' }), responseType: 'blob' });
  }
}
