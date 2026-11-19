import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Contrato, Empresa, EmpresaCambios, ContratoNuevo, ContratoResumen, Evaluacion, Pagina, Pesos, Politica, PoliticaNueva, Presupuesto, PresupuestoNuevo, Proveedor,
  ProveedorCambios,
} from '../api/modelos';
import { api, parametros } from '../http/api';

/** Reglas de la empresa: políticas de aprobación, pesos de evaluación, presupuestos, contratos y proveedores. */
@Injectable({ providedIn: 'root' })
export class ConfiguracionService {
  private readonly http = inject(HttpClient);

  /** Cambia los datos generales y las reglas de la empresa (solo ADMIN_SISTEMA). @param datos datos nuevos */
  actualizarEmpresa(datos: EmpresaCambios): Observable<Empresa> {
    return this.http.put<Empresa>(api('/api/empresa'), datos);
  }

  // ---- Políticas de aprobación ----

  /** Todas las políticas (activas e inactivas) de solicitudes y de adjudicaciones. */
  politicas(): Observable<Politica[]> {
    return this.http.get<Politica[]>(api('/api/politicas-aprobacion'));
  }

  /** @param datos tipo, rol que aprueba y rango de montos [desde, hasta); entre las activas del mismo tipo no pueden superponerse */
  crearPolitica(datos: PoliticaNueva): Observable<Politica> {
    return this.http.post<Politica>(api('/api/politicas-aprobacion'), datos);
  }

  /** @param id política; @param datos nuevo contenido */
  editarPolitica(id: number, datos: PoliticaNueva): Observable<Politica> {
    return this.http.put<Politica>(api(`/api/politicas-aprobacion/${id}`), datos);
  }

  /** @param id política; @param activar {@code true} la activa (falla si se superpone con otra activa) */
  cambiarPolitica(id: number, activar: boolean): Observable<Politica> {
    return this.http.patch<Politica>(api(`/api/politicas-aprobacion/${id}/${activar ? 'activar' : 'desactivar'}`), {});
  }

  // ---- Pesos de evaluación ----

  /** Pesos vigentes de los criterios con que se calcula el análisis de cotizaciones. */
  pesos(): Observable<Pesos> {
    return this.http.get<Pesos>(api('/api/config-pesos'));
  }

  /**
   * Crea una versión nueva de los pesos (la anterior se cierra); los análisis ya hechos conservan los suyos.
   *
   * @param pesos peso de cada criterio por código; deben estar todos y sumar 100
   */
  cambiarPesos(pesos: Record<string, number>): Observable<Pesos> {
    return this.http.post<Pesos>(api('/api/config-pesos'), { pesos });
  }

  // ---- Presupuestos ----

  /** @param f centro de costo y año (vacíos = todos) */
  presupuestos(f: { centroCostoId?: number | null; anio?: number | null }): Observable<Presupuesto[]> {
    return this.http.get<Presupuesto[]>(api('/api/presupuestos'), { params: parametros({ centroCostoId: f.centroCostoId, anio: f.anio }) });
  }

  /** @param datos centro de costo, año, mes y monto; uno por centro y mes */
  crearPresupuesto(datos: PresupuestoNuevo): Observable<Presupuesto> {
    return this.http.post<Presupuesto>(api('/api/presupuestos'), datos);
  }

  /** @param id presupuesto; @param monto nuevo monto (lo único que se cambia) */
  editarPresupuesto(id: number, monto: number): Observable<Presupuesto> {
    return this.http.put<Presupuesto>(api(`/api/presupuestos/${id}`), { monto });
  }

  // ---- Contratos y precios pactados ----

  /**
   * @param f      proveedor, estado y si solo los vigentes hoy
   * @param pagina número de página desde 0
   */
  contratos(f: { proveedorId?: number | null; estado?: string | null; vigente?: boolean | null }, pagina: number): Observable<Pagina<ContratoResumen>> {
    return this.http.get<Pagina<ContratoResumen>>(api('/api/contratos'), { params: parametros({ ...f, page: pagina, size: 20 }) });
  }

  /** @param id contrato; detalle con los precios y lo consumido de cada tope */
  contrato(id: number): Observable<Contrato> {
    return this.http.get<Contrato>(api(`/api/contratos/${id}`));
  }

  /** @param datos proveedor, número, vigencia y precios pactados por producto */
  crearContrato(datos: ContratoNuevo): Observable<Contrato> {
    return this.http.post<Contrato>(api('/api/contratos'), datos);
  }

  /** @param id contrato; @param vigenteHasta nueva fecha de fin (ISO) */
  cambiarVigencia(id: number, vigenteHasta: string): Observable<Contrato> {
    return this.http.patch<Contrato>(api(`/api/contratos/${id}/vigencia`), { vigenteHasta });
  }

  /** @param id contrato; @param motivo obligatorio; un contrato anulado deja de ofrecerse para órdenes directas */
  anularContrato(id: number, motivo: string): Observable<Contrato> {
    return this.http.patch<Contrato>(api(`/api/contratos/${id}/anular`), { motivo });
  }

  // ---- Proveedores ----

  /** @param id proveedor */
  proveedor(id: number): Observable<Proveedor> {
    return this.http.get<Proveedor>(api(`/api/proveedores/${id}`));
  }

  /** @param id proveedor; @param datos contacto y categorías en que cotiza */
  editarProveedor(id: number, datos: ProveedorCambios): Observable<Proveedor> {
    return this.http.put<Proveedor>(api(`/api/proveedores/${id}`), datos);
  }

  /** @param id proveedor suspendido o pendiente; lo habilita y activa a sus usuarios del portal */
  habilitarProveedor(id: number): Observable<Proveedor> {
    return this.http.patch<Proveedor>(api(`/api/proveedores/${id}/habilitar`), {});
  }

  /** @param id proveedor habilitado; @param motivo obligatorio; deja de poder cotizar */
  suspenderProveedor(id: number, motivo: string): Observable<Proveedor> {
    return this.http.patch<Proveedor>(api(`/api/proveedores/${id}/suspender`), { motivo });
  }

  /**
   * Calificaciones que ha recibido el proveedor al cerrar recepciones.
   *
   * @param id     proveedor
   * @param pagina número de página desde 0
   */
  evaluaciones(id: number, pagina: number): Observable<Pagina<Evaluacion>> {
    return this.http.get<Pagina<Evaluacion>>(api(`/api/proveedores/${id}/evaluaciones`), { params: parametros({ page: pagina, size: 10 }) });
  }
}
