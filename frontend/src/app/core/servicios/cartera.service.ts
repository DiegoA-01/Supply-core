import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Abono, ClienteCartera, DetalleCartera, MedioPago, ResumenCartera } from '../api/modelos';
import { api } from '../http/api';

/**
 * Cartera (spec 007): saldos y edades, cuentas de un cliente, excepción de crédito y abonos. Las edades, saldos y la
 * aplicación de los abonos los calcula el backend.
 */
@Injectable({ providedIn: 'root' })
export class CarteraService {
  private readonly http = inject(HttpClient);

  /** Saldos por cliente y totales por edad. */
  resumen(): Observable<ResumenCartera> {
    return this.http.get<ResumenCartera>(api('/api/cartera'));
  }

  /** @param clienteId cliente; sus cuentas y abonos */
  detalle(clienteId: number): Observable<DetalleCartera> {
    return this.http.get<DetalleCartera>(api(`/api/cartera/clientes/${clienteId}`));
  }

  /**
   * Habilita a un cliente en mora para comprar a crédito hasta una fecha.
   *
   * @param clienteId cliente
   * @param hasta     último día (aaaa-mm-dd)
   * @param motivo    por qué se habilita
   */
  habilitar(clienteId: number, hasta: string, motivo: string): Observable<ClienteCartera> {
    return this.http.put<ClienteCartera>(api(`/api/cartera/clientes/${clienteId}/excepcion`), { hasta, motivo });
  }

  /** @param clienteId cliente; retira su excepción de crédito */
  quitarExcepcion(clienteId: number): Observable<ClienteCartera> {
    return this.http.delete<ClienteCartera>(api(`/api/cartera/clientes/${clienteId}/excepcion`));
  }

  /**
   * Abono en el turno de la sesión.
   *
   * @param clienteId  cliente
   * @param valor      valor
   * @param medioPago  medio
   * @param referencia autorización o transferencia
   */
  abonar(clienteId: number, valor: number, medioPago: MedioPago, referencia: string | null): Observable<Abono> {
    return this.http.post<Abono>(api('/api/abonos'), { clienteId, valor, medioPago, referencia });
  }
}
