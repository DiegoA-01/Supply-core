import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from '../http/auth.interceptor';
import { SesionService } from './sesion.service';

const respuesta = (token: string, refresh: string) => ({
  token, tipo: 'Bearer', expiraEnSegundos: 900, refreshToken: refresh,
  usuario: { id: 7, nombre: 'Ana Compras', correo: 'ana@x.co', roles: ['COMPRADOR'] },
});

describe('SesionService + authInterceptor', () => {
  let sesion: SesionService;
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    sesion = TestBed.inject(SesionService);
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('guarda el JWT en memoria y el refresh en sessionStorage, y lo envía en cada llamada', () => {
    sesion.ingresar('el-tornillo', 'ana@x.co', 'secreta').subscribe();
    const login = backend.expectOne('/api/auth/login');
    expect(login.request.headers.has('Authorization')).toBe(false);
    login.flush(respuesta('jwt-1', 'r-1'));

    expect(sesion.tieneRol(['COMPRADOR'])).toBe(true);
    expect(sesion.tieneRol(['ADMIN_SISTEMA'])).toBe(false);
    expect(sessionStorage.getItem('sc.refresh')).toBe('r-1');

    http.get('/api/ordenes').subscribe();
    expect(backend.expectOne('/api/ordenes').request.headers.get('Authorization')).toBe('Bearer jwt-1');
  });

  it('si el JWT venció lo renueva una sola vez y repite la petición', () => {
    sesion.ingresar('el-tornillo', 'ana@x.co', 'secreta').subscribe();
    backend.expectOne('/api/auth/login').flush(respuesta('jwt-1', 'r-1'));

    let resultado: unknown;
    http.get('/api/ordenes').subscribe(r => (resultado = r));
    backend.expectOne('/api/ordenes').flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });

    const refresh = backend.expectOne('/api/auth/refresh');
    expect(refresh.request.body).toEqual({ refreshToken: 'r-1' });
    refresh.flush(respuesta('jwt-2', 'r-2'));

    const reintento = backend.expectOne('/api/ordenes');
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    reintento.flush({ ok: true });
    expect(resultado).toEqual({ ok: true });
    expect(sessionStorage.getItem('sc.refresh')).toBe('r-2');
  });

  it('las rutas de la plataforma no llevan el token de la empresa', () => {
    sesion.ingresar('el-tornillo', 'ana@x.co', 'secreta').subscribe();
    backend.expectOne('/api/auth/login').flush(respuesta('jwt-1', 'r-1'));

    http.get('/api/plataforma/empresas').subscribe({ error: () => undefined });
    expect(backend.expectOne('/api/plataforma/empresas').request.headers.has('Authorization')).toBe(false);
  });
});
