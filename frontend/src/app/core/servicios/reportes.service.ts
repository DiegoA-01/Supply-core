import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EstadoCuenta, Gasto, Pagina, Proveedor, ProveedorRanking } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Reportes de solo lectura y proveedores. Todos los totales los calcula el backend. */
@Injectable({ providedIn: 'root' })
export class ReportesService {
  private readonly http = inject(HttpClient);

  /**
   * Gasto de las órdenes vigentes, con totales por mes, categoría y proveedor.
   *
   * @param desde fecha ISO
   * @param hasta fecha ISO
   */
  gasto(desde: string, hasta: string): Observable<Gasto> {
    return this.http.get<Gasto>(api('/api/reportes/gasto'), { params: parametros({ desde, hasta }) });
  }

  /** Ranking de proveedores por score en el periodo. */
  ranking(desde: string, hasta: string): Observable<ProveedorRanking[]> {
    return this.http.get<ProveedorRanking[]>(api('/api/reportes/proveedores'), { params: parametros({ desde, hasta }) });
  }

  /**
   * Comprado, facturado, pagado y saldo de un proveedor.
   *
   * @param proveedorId proveedor
   * @param desde       fecha ISO
   * @param hasta       fecha ISO
   */
  estadoCuenta(proveedorId: number, desde: string, hasta: string): Observable<EstadoCuenta> {
    return this.http.get<EstadoCuenta>(api(`/api/reportes/proveedores/${proveedorId}/estado-cuenta`), { params: parametros({ desde, hasta }) });
  }

  /** @param estado PENDIENTE, HABILITADO… (vacío = todos) */
  proveedores(estado: string | null): Observable<Pagina<Proveedor>> {
    return this.http.get<Pagina<Proveedor>>(api('/api/proveedores'), { params: parametros({ estado, size: 100, sort: 'razonSocial,asc' }) });
  }

  /** @param id proveedor pendiente */
  habilitar(id: number): Observable<Proveedor> {
    return this.http.patch<Proveedor>(api(`/api/proveedores/${id}/habilitar`), {});
  }

  /**
   * @param id     proveedor pendiente
   * @param motivo motivo del rechazo
   */
  rechazar(id: number, motivo: string): Observable<Proveedor> {
    return this.http.patch<Proveedor>(api(`/api/proveedores/${id}/rechazar`), { motivo });
  }
}

/** Primer y último día del mes de una fecha, en formato ISO (yyyy-mm-dd). */
export function rangoMes(base = new Date()): { desde: string; hasta: string } {
  const desde = new Date(base.getFullYear(), base.getMonth(), 1);
  const hasta = new Date(base.getFullYear(), base.getMonth() + 1, 0);
  return { desde: iso(desde), hasta: iso(hasta) };
}

/** @param fecha fecha local */
export function iso(fecha: Date): string {
  const m = `${fecha.getMonth() + 1}`.padStart(2, '0');
  const d = `${fecha.getDate()}`.padStart(2, '0');
  return `${fecha.getFullYear()}-${m}-${d}`;
}
