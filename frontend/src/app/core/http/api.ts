import { HttpErrorResponse, HttpParams } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { ErrorApi } from '../api/modelos';

/**
 * URL completa de un endpoint del backend.
 *
 * @param ruta ruta que empieza por `/api/`
 */
export function api(ruta: string): string {
  return `${environment.apiUrl}${ruta}`;
}

/**
 * Parámetros de consulta sin los vacíos ({@code null}, {@code undefined} o texto en blanco).
 *
 * @param valores filtros de la pantalla
 */
export function parametros(valores: Record<string, string | number | boolean | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [clave, valor] of Object.entries(valores)) {
    if (valor !== null && valor !== undefined && `${valor}`.trim() !== '') {
      p = p.set(clave, `${valor}`);
    }
  }
  return p;
}

/**
 * Mensaje para mostrar a partir de un error del backend ({@code ErrorResponse}) o de la red.
 *
 * @param error error de HttpClient
 */
export function mensajeError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'No hay conexión con el servidor. Revisa tu red e intenta de nuevo.';
    }
    const cuerpo = error.error as Partial<ErrorApi> | null;
    if (cuerpo?.mensaje) {
      const detalles = cuerpo.detalles?.length ? `: ${cuerpo.detalles.join('; ')}` : '';
      return `${cuerpo.mensaje}${detalles}`;
    }
    if (error.status === 403) {
      return 'No tienes permiso para esta acción.';
    }
  }
  return 'Ocurrió un error inesperado. Intenta de nuevo.';
}

/**
 * Código del error del backend (p. ej. {@code PRESUPUESTO_EXCEDIDO}), si lo hay.
 *
 * @param error error de HttpClient
 */
export function codigoError(error: unknown): string | null {
  return error instanceof HttpErrorResponse ? ((error.error as Partial<ErrorApi> | null)?.codigo ?? null) : null;
}
