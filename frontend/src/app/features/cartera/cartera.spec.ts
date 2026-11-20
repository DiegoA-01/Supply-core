import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Abono, ClienteCartera, ResumenCartera } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { htmlRecibo } from '../pos/tiquete';
import { Cartera } from './cartera';

const enMora: ClienteCartera = { clienteId: 4, nombre: 'Ferretería cliente', documento: 'CC 55667788', cupo: 30000, plazoDias: 30,
  saldo: 25000, disponible: 5000, saldoFavor: 0, alDia: 10000, d1a30: 15000, d31a60: 0, d61a90: 0, mas90: 0, enMora: true, bloqueado: true };
const alDia: ClienteCartera = { ...enMora, clienteId: 5, nombre: 'Taller al día', saldo: 1000, alDia: 1000, d1a30: 0, enMora: false, bloqueado: false };
const resumen: ResumenCartera = { clientes: [enMora, alDia], saldo: 26000, alDia: 11000, d1a30: 15000, d31a60: 0, d61a90: 0, mas90: 0, enMora: 1 };
const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null), tieneModulo: () => true } });

describe('Cartera', () => {
  let fixture: ComponentFixture<Cartera>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** @param roles roles de la sesión */
  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Cartera);
    fixture.detectChanges();
    backend.expectOne('/api/cartera').flush(resumen);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra las edades del servidor y filtra los clientes en mora', () => {
    crear(['CAJERO']);
    expect(texto()).toContain('Bloqueado por mora');
    expect(texto()).toContain('Taller al día');
    c()['filtro'].set('mora');
    fixture.detectChanges();
    expect(c()['clientes']().map((x: ClienteCartera) => x.clienteId)).toEqual([4]);
    expect(c()['decide']()).toBe(false);
  });

  it('el supervisor habilita a un cliente en mora hasta una fecha con motivo', () => {
    crear(['SUPERVISOR_VENTAS']);
    c()['ver'](enMora);
    backend.expectOne('/api/cartera/clientes/4').flush({ cliente: enMora, cuentas: [], abonos: [] });
    fixture.detectChanges();
    expect(texto()).toContain('Habilitar crédito aunque esté en mora');
    c()['hasta'].set('2026-11-27');
    c()['motivo'].set('Paga el viernes');
    c()['habilitar']();
    const r = backend.expectOne('/api/cartera/clientes/4/excepcion');
    expect(r.request.method).toBe('PUT');
    expect(r.request.body).toEqual({ hasta: '2026-11-27', motivo: 'Paga el viernes' });
    r.flush({ ...enMora, bloqueado: false, excepcionHasta: '2026-11-27' });
    backend.expectOne('/api/cartera').flush(resumen);
    backend.expectOne('/api/cartera/clientes/4').flush({ cliente: { ...enMora, bloqueado: false, excepcionHasta: '2026-11-27' }, cuentas: [], abonos: [] });
  });
});

describe('Recibo de abono', () => {
  it('trae el consecutivo, a qué ventas se aplicó y el saldo que queda', () => {
    const a: Abono = { id: 1, consecutivo: 'AB-000001', cliente: 'Ferretería <cliente>', documento: 'CC 1', valor: 5000, medioPago: 'EFECTIVO',
      usuario: 'Pedro', creadoEn: '2026-11-20T10:00:00', aplicaciones: [{ cuentaId: 1, venta: 'C1-000003', valor: 5000 }], saldoPendiente: 20000 };
    const html = htmlRecibo(a);
    expect(html).toContain('AB-000001');
    expect(html).toContain('C1-000003');
    expect(html).toContain('Saldo pendiente');
    expect(html).toContain('Ferretería &lt;cliente&gt;');
  });
});
