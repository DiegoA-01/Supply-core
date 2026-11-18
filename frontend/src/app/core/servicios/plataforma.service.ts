import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AuditoriaPlataforma, EmpresaCrear, EmpresaDetalle, EmpresaPlataforma, EstadoSuscripcion, ModuloPlataforma, Pagina, Plan,
  PlanCambios, PlanNuevo,
} from '../api/modelos';
import { api, parametros } from '../http/api';

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

  /** @param plan código, nombre y módulos que incluye */
  crearPlan(plan: PlanNuevo): Observable<Plan> {
    return this.http.post<Plan>(api('/api/plataforma/planes'), plan);
  }

  /** @param id plan; @param cambios nombre, módulos o estado activo */
  editarPlan(id: number, cambios: PlanCambios): Observable<Plan> {
    return this.http.put<Plan>(api(`/api/plataforma/planes/${id}`), cambios);
  }

  /** Catálogo de módulos que se pueden vender. */
  modulos(): Observable<ModuloPlataforma[]> {
    return this.http.get<ModuloPlataforma[]>(api('/api/plataforma/modulos'));
  }

  /** @param id empresa; @param planCodigo plan nuevo */
  cambiarPlan(id: number, planCodigo: string): Observable<EmpresaDetalle> {
    return this.http.put<EmpresaDetalle>(api(`/api/plataforma/empresas/${id}/plan`), { planCodigo });
  }

  /**
   * Auditoría global de la plataforma (accesos, altas, planes, módulos y estados).
   *
   * @param empresaId filtro opcional
   * @param pagina    número de página
   */
  auditoria(empresaId: number | null, pagina: number): Observable<Pagina<AuditoriaPlataforma>> {
    return this.http.get<Pagina<AuditoriaPlataforma>>(api('/api/plataforma/auditoria'),
      { params: parametros({ empresaId, page: pagina, size: 30, sort: 'creadoEn,desc' }) });
  }
}
