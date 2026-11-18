import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CentroCosto, CorreoExportacion, DecisionSolicitud, Pagina, Politica, Presupuesto, Solicitud, SolicitudNueva, SolicitudResumen, Usuario,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Solicitudes de compra, aprobación y presupuesto. */
@Injectable({ providedIn: 'root' })
export class ComprasService {
  private readonly http = inject(HttpClient);

  /** Bandeja del aprobador (incluye las delegadas). */
  pendientes(tamano = 50): Observable<Pagina<SolicitudResumen>> {
    return this.http.get<Pagina<SolicitudResumen>>(api('/api/solicitudes/pendientes-aprobacion'),
      { params: parametros({ size: tamano, sort: 'enviadoEn,asc' }) });
  }

  /**
   * Solicitudes visibles para el usuario (el solicitante solo ve las suyas).
   *
   * @param filtros estado y texto del consecutivo
   */
  buscar(filtros: { estado?: string | null; texto?: string | null; tamano?: number }): Observable<Pagina<SolicitudResumen>> {
    return this.http.get<Pagina<SolicitudResumen>>(api('/api/solicitudes'), {
      params: parametros({ estado: filtros.estado, texto: filtros.texto, size: filtros.tamano ?? 50, sort: 'creadoEn,desc' }),
    });
  }

  /** @param id solicitud */
  obtener(id: number): Observable<Solicitud> {
    return this.http.get<Solicitud>(api(`/api/solicitudes/${id}`));
  }

  /**
   * Aprueba, devuelve o rechaza.
   *
   * @param id            solicitud
   * @param decision      decisión
   * @param observaciones obligatorias al devolver o rechazar
   */
  decidir(id: number, decision: DecisionSolicitud, observaciones: string): Observable<Solicitud> {
    return this.http.post<Solicitud>(api(`/api/solicitudes/${id}/decision`), { decision, observaciones });
  }

  /**
   * Crea la solicitud en borrador; el centro de costo sale del usuario de la sesión.
   *
   * @param datos motivo, fecha requerida y líneas
   */
  crear(datos: SolicitudNueva): Observable<Solicitud> {
    return this.http.post<Solicitud>(api('/api/solicitudes'), datos);
  }

  /**
   * Edita una solicitud propia en borrador o devuelta.
   *
   * @param id    solicitud
   * @param datos motivo, fecha requerida y líneas
   */
  actualizar(id: number, datos: SolicitudNueva): Observable<Solicitud> {
    return this.http.put<Solicitud>(api(`/api/solicitudes/${id}`), datos);
  }

  /**
   * Envía a aprobación. Si excede el presupuesto responde 409 PRESUPUESTO_EXCEDIDO: con justificación se envía
   * como excepción.
   *
   * @param id                     solicitud
   * @param justificacionExcepcion solo si excede el presupuesto
   */
  enviar(id: number, justificacionExcepcion?: string): Observable<Solicitud> {
    return this.http.post<Solicitud>(api(`/api/solicitudes/${id}/enviar`), { justificacionExcepcion: justificacionExcepcion || undefined });
  }

  /** @param id solicitud abierta que ya no se necesita */
  cancelar(id: number): Observable<Solicitud> {
    return this.http.post<Solicitud>(api(`/api/solicitudes/${id}/cancelar`), {});
  }

  /** Centros de costo de la empresa. */
  centros(): Observable<CentroCosto[]> {
    return this.http.get<CentroCosto[]>(api('/api/centros-costo'));
  }

  /**
   * Presupuestos de un centro de costo en un año.
   *
   * @param centroCostoId centro
   * @param anio          año
   */
  presupuestos(centroCostoId: number, anio: number): Observable<Presupuesto[]> {
    return this.http.get<Presupuesto[]>(api('/api/presupuestos'), { params: parametros({ centroCostoId, anio }) });
  }

  /** Políticas de aprobación de la empresa (quién aprueba según el monto). */
  politicas(): Observable<Politica[]> {
    return this.http.get<Politica[]>(api('/api/politicas-aprobacion'));
  }

  /** @param filtros los mismos de la lista; Excel con las solicitudes que el usuario puede ver */
  exportarSolicitudes(filtros: { estado?: string | null; texto?: string | null }): Observable<Blob> {
    return this.http.get(api('/api/exportaciones/solicitudes'),
      { params: parametros({ formato: 'xlsx', estado: filtros.estado, texto: filtros.texto }), responseType: 'blob' });
  }

  /** @param filtros los mismos de la lista; envía el Excel al correo del usuario de la sesión */
  solicitudesPorCorreo(filtros: { estado?: string | null; texto?: string | null }): Observable<CorreoExportacion> {
    return this.http.post<CorreoExportacion>(api('/api/exportaciones/solicitudes/correo'), {},
      { params: parametros({ formato: 'xlsx', estado: filtros.estado, texto: filtros.texto }) });
  }

  /** @param id usuario (solo ADMIN_SISTEMA y AUDITOR pueden leerlo) */
  usuario(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(api(`/api/usuarios/${id}`));
  }
}
