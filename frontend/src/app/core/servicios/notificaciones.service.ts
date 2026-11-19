import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ConfigNotificacion, Notificacion, Pagina } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Bandeja interna de notificaciones (campana del encabezado). */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  private readonly http = inject(HttpClient);

  /** Cantidad sin leer. */
  readonly noLeidas = signal(0);

  /** Actualiza el contador sin leer. */
  contar(): void {
    this.http.get<{ cantidad?: number }>(api('/api/notificaciones/no-leidas'))
      .subscribe({ next: r => this.noLeidas.set(r.cantidad ?? 0), error: () => undefined });
  }

  /** Las más recientes. */
  recientes(): Observable<Pagina<Notificacion>> {
    return this.http.get<Pagina<Notificacion>>(api('/api/notificaciones'), { params: parametros({ size: 12, sort: 'creadoEn,desc' }) });
  }

  /** Configuración de la empresa: qué eventos generan correo y cuáles llegan a la campana (solo ADMIN_SISTEMA). */
  configuracion(): Observable<ConfigNotificacion[]> {
    return this.http.get<ConfigNotificacion[]>(api('/api/config-notificaciones'));
  }

  /** @param id canal de un evento; @param activo nuevo estado */
  cambiarConfiguracion(id: number, activo: boolean): Observable<ConfigNotificacion> {
    return this.http.patch<ConfigNotificacion>(api(`/api/config-notificaciones/${id}`), { activo });
  }

  /**
   * Activa o desactiva un canal de un evento por su nombre; el backend crea la configuración si la empresa no la tenía.
   *
   * @param evento evento del aviso
   * @param canal  EMAIL o INTERNA
   * @param activo nuevo estado
   */
  cambiarPorEvento(evento: string, canal: string, activo: boolean): Observable<ConfigNotificacion> {
    return this.http.put<ConfigNotificacion>(api('/api/config-notificaciones'), { evento, canal, activo });
  }

  /** Marca todas como leídas. */
  marcarTodas(): Observable<unknown> {
    return this.http.patch(api('/api/notificaciones/leidas'), {}).pipe(tap(() => this.noLeidas.set(0)));
  }
}
