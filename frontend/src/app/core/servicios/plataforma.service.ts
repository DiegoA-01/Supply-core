import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { EmpresaCrear, EmpresaDetalle, EmpresaPlataforma, EstadoSuscripcion, Plan } from '../api/modelos';
import { api } from '../http/api';

/** Consola del dueño: empresas, planes, módulos y estado de la suscripción. No toca datos de negocio. */
@Injectable({ providedIn: 'root' })
export class PlataformaService {
  private readonly http = inject(HttpClient);

  empresas(): Observable<EmpresaPlataforma[]> {
    return this.http.get<EmpresaPlataforma[]>(api('/api/plataforma/empresas'));
  }

  /** @param id empresa */
  empresa(id: number): Observable<EmpresaDetalle> {
    return this.http.get<EmpresaDetalle>(api(`/api/plataforma/empresas/${id}`));
  }

  /** Alta de una empresa con su administrador inicial. */
  crear(datos: EmpresaCrear): Observable<EmpresaDetalle> {
    return this.http.post<EmpresaDetalle>(api('/api/plataforma/empresas'), datos);
  }

  /**
   * Prende o apaga un módulo como excepción al plan.
   *
   * @param id         empresa
   * @param modulo     código del módulo
   * @param habilitado nuevo estado
   */
  modulo(id: number, modulo: string, habilitado: boolean): Observable<EmpresaDetalle> {
    return this.http.put<EmpresaDetalle>(api(`/api/plataforma/empresas/${id}/modulos/${modulo}`), { habilitado });
  }

  /**
   * @param id     empresa
   * @param estado nuevo estado de la suscripción
   * @param motivo motivo (obligatorio al suspender)
   */
  estado(id: number, estado: EstadoSuscripcion, motivo: string): Observable<EmpresaDetalle> {
    return this.http.patch<EmpresaDetalle>(api(`/api/plataforma/empresas/${id}/estado`), { estado, motivo });
  }

  planes(): Observable<Plan[]> {
    return this.http.get<Plan[]>(api('/api/plataforma/planes'));
  }
}
