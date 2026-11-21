import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AccionAutorizada, Autorizacion, Caja, CajaRequest, ConfiguracionCaja, EstadoTurno, MedioPago, Pagina, Turno,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Cajas, turnos y autorizaciones con PIN (spec 003). Esperados y diferencias los calcula el backend. */
@Injectable({ providedIn: 'root' })
export class CajaService {
  private readonly http = inject(HttpClient);

  /** @param activa filtro por estado (null = todas) */
  cajas(activa: boolean | null = null): Observable<Caja[]> {
    return this.http.get<Caja[]>(api('/api/cajas'), { params: parametros({ activa }) });
  }

  /** @param datos caja nueva */
  crearCaja(datos: CajaRequest): Observable<Caja> {
    return this.http.post<Caja>(api('/api/cajas'), datos);
  }

  /** @param id caja; @param datos datos nuevos */
  editarCaja(id: number, datos: CajaRequest): Observable<Caja> {
    return this.http.put<Caja>(api(`/api/cajas/${id}`), datos);
  }

  configuracion(): Observable<ConfiguracionCaja> {
    return this.http.get<ConfiguracionCaja>(api('/api/cajas/configuracion'));
  }

  /**
   * @param toleranciaDiferencia diferencia aceptada sin aprobación
   * @param bodegaAveriasId      bodega para lo devuelto en mal estado (null = no reingresa)
   * @param umbrales             anulaciones y descuentos por cajero y día que avisan al dueño (spec 010; vacío = no cambian)
   */
  configurar(toleranciaDiferencia: number, bodegaAveriasId: number | null = null,
    umbrales: { anulaciones: number | null; descuentos: number | null } | null = null): Observable<ConfiguracionCaja> {
    return this.http.put<ConfiguracionCaja>(api('/api/cajas/configuracion'), {
      toleranciaDiferencia, bodegaAveriasId,
      umbralAnulacionesDia: umbrales?.anulaciones ?? null, umbralDescuentosDia: umbrales?.descuentos ?? null,
    });
  }

  /** @param filtros estado, solo pendientes de aprobación, caja y página */
  turnos(filtros: { estado?: EstadoTurno | null; pendientes?: boolean; cajaId?: number | null; pagina?: number }): Observable<Pagina<Turno>> {
    return this.http.get<Pagina<Turno>>(api('/api/turnos'), { params: parametros({
      estado: filtros.estado, pendientes: filtros.pendientes ?? false, cajaId: filtros.cajaId, page: filtros.pagina ?? 0, size: 20 }) });
  }

  /** @param id turno; detalle con el libro de caja (tiquete Z si está cerrado) */
  turno(id: number): Observable<Turno> {
    return this.http.get<Turno>(api(`/api/turnos/${id}`));
  }

  /** @param id turno; @param explicacion qué pasó con la diferencia */
  aprobar(id: number, explicacion: string): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${id}/aprobar`), { explicacion });
  }

  /** Turno abierto de la sesión (404 si no tiene). */
  actual(): Observable<Turno> {
    return this.http.get<Turno>(api('/api/turnos/actual'));
  }

  /** @param cajaId caja; @param baseApertura efectivo contado al abrir */
  abrir(cajaId: number, baseApertura: number): Observable<Turno> {
    return this.http.post<Turno>(api('/api/turnos'), { cajaId, baseApertura });
  }

  /**
   * Retiro, gasto o ingreso con autorización.
   *
   * @param turnoId turno
   * @param datos   tipo, monto, motivo, soporte y token de autorización
   */
  movimiento(turnoId: number, datos: { tipo: 'RETIRO' | 'GASTO' | 'INGRESO'; monto: number; motivo: string; soporte?: string | null;
    autorizacion: string }): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${turnoId}/movimientos`), datos);
  }

  /**
   * Cierre ciego.
   *
   * @param turnoId         turno
   * @param contadoEfectivo efectivo contado
   * @param contadoOtros    vouchers y transferencias por medio
   * @param observacion     nota opcional
   */
  cerrar(turnoId: number, contadoEfectivo: number, contadoOtros: Partial<Record<MedioPago, number>>, observacion: string | null): Observable<Turno> {
    return this.http.post<Turno>(api(`/api/turnos/${turnoId}/cerrar`), { contadoEfectivo, contadoOtros, observacion });
  }

  /**
   * El supervisor escribe correo y PIN en la pantalla del cajero; devuelve un permiso de un solo uso.
   *
   * @param datos correo, PIN, acción, turno y referencia
   */
  autorizar(datos: { correo: string; pin: string; accion: AccionAutorizada; turnoId: number; referenciaId?: number | null }): Observable<Autorizacion> {
    return this.http.post<Autorizacion>(api('/api/autorizaciones'), datos);
  }

  /** Si la sesión ya fijó su PIN de supervisor. */
  tienePin(): Observable<{ tienePin: boolean }> {
    return this.http.get<{ tienePin: boolean }>(api('/api/autorizaciones/mi-pin'));
  }

  /** @param passwordActual contraseña de la cuenta; @param pin PIN nuevo de 6 dígitos */
  cambiarPin(passwordActual: string, pin: string): Observable<void> {
    return this.http.put<void>(api('/api/autorizaciones/mi-pin'), { passwordActual, pin });
  }
}
