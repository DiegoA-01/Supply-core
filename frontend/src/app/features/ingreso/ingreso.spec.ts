import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from '../../core/http/auth.interceptor';
import { SesionService } from '../../core/sesion/sesion.service';
import { Ingreso } from './ingreso';
import { Recuperar } from './recuperar';

const sesionCompleta = {
  token: 'jwt-1', tipo: 'Bearer', expiraEnSegundos: 900, refreshToken: 'r-1',
  usuario: { id: 7, nombre: 'Ana Admin', correo: 'ana@x.co', roles: ['ADMIN_SISTEMA'] },
};

function configurar(): HttpTestingController {
  sessionStorage.clear();
  TestBed.configureTestingModule({
    providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
  });
  return TestBed.inject(HttpTestingController);
}

describe('Ingreso con segundo factor', () => {
  let fixture: ComponentFixture<Ingreso>;
  let backend: HttpTestingController;

  beforeEach(() => {
    backend = configurar();
    fixture = TestBed.createComponent(Ingreso);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const escribirYEnviar = () => {
    fixture.componentInstance['form'].setValue({ empresa: 'demo', correo: 'ana@x.co', password: 'Secreta123' });
    fixture.componentInstance['ingresar']();
  };

  it('si el login trae desafío no abre sesión y pide el código (con la clave si es la primera vez)', () => {
    escribirYEnviar();
    backend.expectOne('/api/auth/login').flush({
      desafioMfa: 'desafio-jwt', mfaConfigurado: false, secretoMfa: 'JBSWY3DPEHPK3PXP', otpauthUri: 'otpauth://totp/x',
    });
    fixture.detectChanges();

    expect(TestBed.inject(SesionService).autenticado()).toBe(false);
    expect(texto()).toContain('Código de verificación');
    expect(texto()).toContain('JBSWY3DPEHPK3PXP');
  });

  it('el código de 6 dígitos se canjea sin token de sesión y abre la sesión', () => {
    escribirYEnviar();
    backend.expectOne('/api/auth/login').flush({ desafioMfa: 'desafio-jwt', mfaConfigurado: true });
    fixture.componentInstance['codigo'].set('123456');
    fixture.componentInstance['confirmar']();

    const peticion = backend.expectOne('/api/auth/mfa');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    expect(peticion.request.body).toEqual({ desafio: 'desafio-jwt', codigo: '123456' });
    peticion.flush(sesionCompleta);
    backend.match('/api/empresa').forEach(r => r.flush({}));
    backend.match('/api/empresa/modulos').forEach(r => r.flush({ modulos: [] }));

    expect(TestBed.inject(SesionService).autenticado()).toBe(true);
  });

  it('al enviar el formulario del código (Enter o botón) se llama al servidor y el navegador no recarga la página', () => {
    escribirYEnviar();
    backend.expectOne('/api/auth/login').flush({ desafioMfa: 'desafio-jwt', mfaConfigurado: true });
    fixture.detectChanges();
    fixture.componentInstance['codigo'].set('123456');

    const envio = new Event('submit', { cancelable: true });
    (fixture.nativeElement as HTMLElement).querySelector('form.formulario')!.dispatchEvent(envio);

    expect(envio.defaultPrevented).toBe(true);
    backend.expectOne('/api/auth/mfa');
  });

  it('un código que no son 6 dígitos no sale al servidor', () => {
    escribirYEnviar();
    backend.expectOne('/api/auth/login').flush({ desafioMfa: 'desafio-jwt', mfaConfigurado: true });
    fixture.componentInstance['codigo'].set('12ab');
    fixture.componentInstance['confirmar']();

    expect(fixture.componentInstance['error']()).toContain('6 dígitos');
  });

  it('sin MFA entra directo', () => {
    const router = TestBed.inject(Router);
    router.navigateByUrl = () => Promise.resolve(true);
    escribirYEnviar();
    backend.expectOne('/api/auth/login').flush(sesionCompleta);
    backend.match('/api/empresa').forEach(r => r.flush({}));
    backend.match('/api/empresa/modulos').forEach(r => r.flush({ modulos: [] }));

    expect(TestBed.inject(SesionService).autenticado()).toBe(true);
  });
});

describe('Recuperar contraseña', () => {
  let fixture: ComponentFixture<Recuperar>;
  let backend: HttpTestingController;

  beforeEach(() => {
    backend = configurar();
    fixture = TestBed.createComponent(Recuperar);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('pide el código y luego lo usa con la contraseña nueva, sin mandar Authorization', () => {
    const c = fixture.componentInstance;
    c['empresa'].set('Ferretería Centro');
    c['correo'].set('ana@x.co');
    c['pedir']();
    const pedido = backend.expectOne('/api/auth/recuperar');
    expect(pedido.request.headers.has('Authorization')).toBe(false);
    expect(pedido.request.body).toEqual({ empresa: 'Ferretería Centro', correo: 'ana@x.co' });
    pedido.flush({ mensaje: 'Si la cuenta existe, te enviamos un correo' });
    expect(c['paso']()).toBe('cambiar');

    c['token'].set('abc123');
    c['nueva'].set('Nueva12345');
    c['confirmacion'].set('Nueva12345');
    c['cambiar']();
    const cambio = backend.expectOne('/api/auth/restablecer');
    expect(cambio.request.body).toEqual({ empresa: 'Ferretería Centro', token: 'abc123', passwordNueva: 'Nueva12345' });
    cambio.flush({ mensaje: 'ok' });
    expect(c['paso']()).toBe('listo');
  });

  it('no envía si la contraseña no cumple las reglas o no coincide', () => {
    const c = fixture.componentInstance;
    c['paso'].set('cambiar');
    c['token'].set('abc123');
    c['nueva'].set('corta');
    c['confirmacion'].set('corta');
    c['cambiar']();

    expect(c['error']()).toContain('reglas');
  });
});
