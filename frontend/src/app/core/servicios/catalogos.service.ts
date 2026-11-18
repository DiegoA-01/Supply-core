import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { api, parametros } from '../http/api';

/** Fila de un catálogo simple: siempre trae id y estado, y los campos propios de ese catálogo. */
export type FilaCatalogo = { id?: number; activo?: boolean } & Record<string, unknown>;

/** Resultado de convertir una cantidad entre dos unidades de la misma magnitud. */
export interface Conversion {
  cantidadOrigen?: number;
  unidadOrigen?: string;
  resultado?: number;
  unidadDestino?: string;
}

/**
 * Catálogos de configuración con el mismo formato (sedes, centros de costo, bodegas, categorías y unidades de medida):
 * lista, crear, editar, activar y desactivar. Desactivar no borra: el histórico conserva lo que ya usó el dato.
 */
@Injectable({ providedIn: 'root' })
export class CatalogosService {
  private readonly http = inject(HttpClient);

  /** @param ruta ruta del catálogo (/api/sedes…); @param activo {@code null} = todos */
  listar(ruta: string, activo: boolean | null): Observable<FilaCatalogo[]> {
    return this.http.get<FilaCatalogo[]>(api(ruta), { params: parametros({ activo }) });
  }

  /** @param ruta ruta del catálogo; @param datos campos del formulario */
  crear(ruta: string, datos: Record<string, unknown>): Observable<FilaCatalogo> {
    return this.http.post<FilaCatalogo>(api(ruta), datos);
  }

  /** @param ruta ruta del catálogo; @param id fila; @param datos campos del formulario */
  actualizar(ruta: string, id: number, datos: Record<string, unknown>): Observable<FilaCatalogo> {
    return this.http.put<FilaCatalogo>(api(`${ruta}/${id}`), datos);
  }

  /** @param ruta ruta del catálogo; @param id fila; @param activar {@code true} la activa, {@code false} la desactiva */
  cambiarEstado(ruta: string, id: number, activar: boolean): Observable<FilaCatalogo> {
    return this.http.patch<FilaCatalogo>(api(`${ruta}/${id}/${activar ? 'activar' : 'desactivar'}`), {});
  }

  /**
   * Cuánto es una cantidad en otra unidad; el cálculo lo hace el backend con los factores de conversión.
   *
   * @param cantidad cantidad en la unidad de origen
   * @param desde    unidad de origen
   * @param hacia    unidad de destino (misma magnitud)
   */
  convertir(cantidad: number, desde: number, hacia: number): Observable<Conversion> {
    return this.http.get<Conversion>(api('/api/unidades-medida/convertir'), { params: parametros({ cantidad, desde, hacia }) });
  }
}
