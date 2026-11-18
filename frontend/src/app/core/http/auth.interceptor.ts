import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';

import { PlataformaSesionService } from '../sesion/plataforma-sesion.service';
import { SesionService } from '../sesion/sesion.service';

/** Marca una petición que ya se reintentó con un token renovado, para no entrar en un ciclo. */
const REINTENTO = new HttpContextToken<boolean>(() => false);

/** Rutas que no llevan token: ingreso, renovación y autorregistro de proveedores. */
const PUBLICAS = ['/api/auth/login', '/api/auth/refresh', '/api/auth/recuperar', '/api/auth/restablecer', '/api/plataforma/auth/',
  '/api/portal/registro/'];

/**
 * Agrega {@code Authorization: Bearer} a las llamadas al backend: el token del dueño en {@code /api/plataforma} y el
 * de la empresa en el resto. Si un token de empresa venció (401), lo renueva una vez y repite la petición; si no se
 * puede, cierra la sesión.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const ruta = req.url.slice(req.url.indexOf('/api/'));
  if (!req.url.includes('/api/') || PUBLICAS.some(p => ruta.startsWith(p)) || ruta.split('?')[0] === '/api/auth/mfa') {
    return next(req);
  }

  if (ruta.startsWith('/api/plataforma/')) {
    const plataforma = inject(PlataformaSesionService);
    const token = plataforma.tokenActual();
    return next(token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req).pipe(
      catchError((e: HttpErrorResponse) => {
        if (e.status === 401) {
          plataforma.salir();
        }
        return throwError(() => e);
      }),
    );
  }

  const sesion = inject(SesionService);
  const token = sesion.tokenActual();
  const conToken = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;
  return next(conToken).pipe(
    catchError((e: HttpErrorResponse) => {
      if (e.status !== 401 || req.context.get(REINTENTO)) {
        return throwError(() => e);
      }
      return sesion.refrescar().pipe(
        catchError(() => {
          sesion.expirada();
          return throwError(() => e);
        }),
        switchMap(r => next(req.clone({
          setHeaders: { Authorization: `Bearer ${r.token}` },
          context: req.context.set(REINTENTO, true),
        }))),
      );
    }),
  );
};
