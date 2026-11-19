import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Turno } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { AutorizacionPin } from './autorizacion-pin';
import { CajasTurnos } from './caja';

const pendiente: Turno = {
  id: 9, cajaId: 1, caja: 'Caja 1', cajero: 'Pedro Cajero', estado: 'CERRADO', abiertoEn: '2026-11-19T08:00:00', baseApertura: 50000,
  cerradoEn: '2026-11-19T18:00:00', requiereAprobacion: true, diferencia: -5000,
  esperado: { EFECTIVO: 110000, TARJETA_DEBITO: 30000 }, contado: { EFECTIVO: 105000, TARJETA_DEBITO: 30000 },
  diferencias: { EFECTIVO: -5000, TARJETA_DEBITO: 0 },
  movimientos: [{ id: 1, tipo: 'BASE', medioPago: 'EFECTIVO', monto: 50000, creadoEn: '2026-11-19T08:00:00' },
    { id: 2, tipo: 'RETIRO', medioPago: 'EFECTIVO', monto: -20000, motivo: 'A la bóveda', autorizadoPor: 'Ana Supervisora', creadoEn: '2026-11-19T12:00:00' }],
};
const pagina = (l: Turno[]) => ({ contenido: l, pagina: 0, tamano: 20, totalElementos: l.length, totalPaginas: 1 });
const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null) } });

describe('Cajas y turnos', () => {
  let fixture: ComponentFixture<CajasTurnos>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CajasTurnos);
    fixture.detectChanges();
    const turnos = backend.expectOne(r => r.url === '/api/turnos');
    expect(turnos.request.params.get('pendientes')).toBe('true');
    turnos.flush(pagina([pendiente]));
    backend.expectOne(r => r.url === '/api/cajas').flush([{ id: 1, nombre: 'Caja 1', sede: 'Centro', bodega: 'Principal', prefijoConsecutivo: 'C1', topeEfectivo: 500000, activa: true, turnoAbierto: false }]);
    backend.expectOne('/api/cajas/configuracion').flush({ toleranciaDiferencia: 2000 });
    if (roles.some(r => r === 'SUPERVISOR_VENTAS' || r === 'ADMIN_SISTEMA')) {
      backend.expectOne('/api/autorizaciones/mi-pin').flush({ tienePin: false });
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el supervisor ve la diferencia por aprobar con el cuadre por medio y la aprueba con su explicación', () => {
    crear(['SUPERVISOR_VENTAS']);
    expect(texto()).toContain('Por aprobar');
    c()['ver'](pendiente);
    backend.expectOne('/api/turnos/9').flush(pendiente);
    fixture.detectChanges();
    expect(texto()).toContain('Tarjeta débito');
    expect(texto()).toContain('Ana Supervisora');
    c()['explicacion'].set('corto');
    c()['aprobar']();
    backend.expectNone('/api/turnos/9/aprobar');
    c()['explicacion'].set('Se pagó un domicilio sin registrarlo');
    c()['aprobar']();
    const req = backend.expectOne('/api/turnos/9/aprobar');
    expect(req.request.body).toEqual({ explicacion: 'Se pagó un domicilio sin registrarlo' });
    req.flush({ ...pendiente, estado: 'APROBADO', aprobadoPor: 'Ana Supervisora' });
    backend.expectOne(r => r.url === '/api/turnos').flush(pagina([]));
  });

  it('el auditor consulta pero no aprueba ni edita cajas', () => {
    crear(['AUDITOR']);
    c()['ver'](pendiente);
    backend.expectOne('/api/turnos/9').flush(pendiente);
    fixture.detectChanges();
    expect(texto()).not.toContain('Aprobar diferencia');
    c()['pestana'].set('cajas');
    fixture.detectChanges();
    expect(texto()).not.toContain('Nueva caja');
    expect(texto()).not.toContain('Mi PIN');
  });

  it('el dueño crea una caja: la sede sale de la bodega elegida', () => {
    crear(['ADMIN_SISTEMA']);
    c()['pestana'].set('cajas');
    c()['editarCaja'](null);
    backend.expectOne(r => r.url === '/api/bodegas').flush([{ id: 3, nombre: 'Principal', sedeId: 7, sedeNombre: 'Centro', activo: true }]);
    c()['cambiar']('nombre', 'Caja 2');
    c()['cambiar']('bodegaId', 3);
    c()['cambiar']('prefijo', 'C2');
    c()['cambiar']('tope', 300000);
    c()['guardarCaja']();
    const req = backend.expectOne(r => r.url === '/api/cajas' && r.method === 'POST');
    expect(req.request.body).toEqual({ nombre: 'Caja 2', sedeId: 7, bodegaId: 3, prefijoConsecutivo: 'C2', topeEfectivo: 300000, activa: true });
    req.flush({ id: 2 });
    backend.expectOne(r => r.url === '/api/cajas' && r.method === 'GET').flush([]);
  });

  it('el PIN pide la contraseña, son 6 dígitos y los campos se vacían al enviar', () => {
    crear(['SUPERVISOR_VENTAS']);
    c()['passwordActual'].set('Secreta123');
    c()['pinNuevo'].set('12345');
    c()['guardarPin']();
    backend.expectNone('/api/autorizaciones/mi-pin');
    c()['pinNuevo'].set('123456');
    c()['guardarPin']();
    const req = backend.expectOne(r => r.url === '/api/autorizaciones/mi-pin' && r.method === 'PUT');
    expect(req.request.body).toEqual({ passwordActual: 'Secreta123', pin: '123456' });
    expect(c()['pinNuevo']()).toBe('');
    expect(c()['passwordActual']()).toBe('');
    req.flush(null);
  });
});

describe('Autorización con PIN', () => {
  let fixture: ComponentFixture<AutorizacionPin>;
  let backend: HttpTestingController;
  const a = () => fixture.componentInstance as unknown as Record<string, any>;

  it('envía correo y PIN en el cuerpo, borra el PIN del campo y entrega el permiso', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AutorizacionPin);
    fixture.componentRef.setInput('accion', 'RETIRO');
    fixture.componentRef.setInput('turnoId', 9);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('el retiro de efectivo');
    let recibido: { token: string; supervisor: string } | null = null;
    (fixture.componentInstance.autorizado as any).subscribe((v: { token: string; supervisor: string }) => (recibido = v));
    a()['correo'].set(' ana@ferreteria.co ');
    a()['pin'].set('12a456');
    a()['enviar']();
    backend.expectNone('/api/autorizaciones');
    a()['pin'].set('123456');
    a()['enviar']();
    const req = backend.expectOne('/api/autorizaciones');
    expect(req.request.url).not.toContain('123456');
    expect(req.request.body).toEqual({ correo: 'ana@ferreteria.co', pin: '123456', accion: 'RETIRO', turnoId: 9, referenciaId: null });
    expect(a()['pin']()).toBe('');
    req.flush({ token: 'permiso-1', venceEn: '2026-11-19T12:02:00', supervisor: 'Ana' });
    expect(recibido).toEqual({ token: 'permiso-1', supervisor: 'Ana' });
    backend.verify();
  });
});
