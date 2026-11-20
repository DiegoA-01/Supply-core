import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Turno } from '../../core/api/modelos';
import { MiTurno } from './mi-turno';

const abierto: Turno = { id: 4, cajaId: 1, caja: 'Caja 1', estado: 'ABIERTO', abiertoEn: '2026-11-20T08:00:00', baseApertura: 50000,
  debeRetirar: true, movimientos: [{ id: 1, tipo: 'BASE', medioPago: 'EFECTIVO', monto: 50000 }] };

describe('Mi turno', () => {
  let fixture: ComponentFixture<MiTurno>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** @param turno turno abierto (null = sin turno) */
  const crear = (turno: Turno | null) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MiTurno);
    fixture.detectChanges();
    const actual = backend.expectOne('/api/turnos/actual');
    if (turno) {
      actual.flush(turno);
    } else {
      actual.flush({}, { status: 404, statusText: 'Not Found' });
      backend.expectOne(r => r.url === '/api/cajas').flush([{ id: 1, nombre: 'Caja 1', sede: 'Centro' }]);
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('sin turno abre uno con la caja y la base', () => {
    crear(null);
    expect(texto()).toContain('Abrir turno');
    c()['cajaId'].set(1);
    c()['base'].set(80000);
    c()['abrir']();
    const r = backend.expectOne('/api/turnos');
    expect(r.request.body).toEqual({ cajaId: 1, baseApertura: 80000 });
    r.flush(abierto);
    fixture.detectChanges();
    expect(texto()).toContain('Cierre ciego');
  });

  it('avisa el tope de efectivo y el retiro va con el permiso del supervisor', () => {
    crear(abierto);
    expect(texto()).toContain('pasó su tope de efectivo');
    c()['tipo'].set('RETIRO');
    c()['monto'].set(30000);
    c()['motivo'].set('A la bóveda');
    c()['registrar']('permiso-9');
    const r = backend.expectOne('/api/turnos/4/movimientos');
    expect(r.request.body).toEqual({ tipo: 'RETIRO', monto: 30000, motivo: 'A la bóveda', soporte: null, autorizacion: 'permiso-9' });
    r.flush({ ...abierto, debeRetirar: false });
  });

  it('el abono de un cliente va al servidor y refresca el turno', () => {
    crear(abierto);
    c()['buscarCliente'].set('5566');
    c()['buscarClientes']();
    backend.expectOne(r => r.url === '/api/clientes').flush({ contenido: [{ id: 4, nombre: 'Ferretería cliente', numeroDocumento: '5566' }] });
    c()['clienteAbono'].set({ id: 4, nombre: 'Ferretería cliente' });
    c()['valorAbono'].set(5000);
    const imprimir = window.print;
    c()['abonar']();
    const r = backend.expectOne('/api/abonos');
    expect(r.request.body).toEqual({ clienteId: 4, valor: 5000, medioPago: 'EFECTIVO', referencia: null });
    r.flush({ id: 1, consecutivo: 'AB-000001', valor: 5000, saldoPendiente: 20000, aplicaciones: [], creadoEn: '2026-11-20T10:00:00' });
    backend.expectOne('/api/turnos/actual').flush(abierto);
    expect(c()['clienteAbono']()).toBeNull();
    window.print = imprimir;
  });

  it('el cierre es ciego: el esperado solo aparece después de guardar', () => {
    crear(abierto);
    expect(texto()).not.toContain('Esperado');
    c()['contar']('EFECTIVO', 79000);
    c()['cerrar']();
    const r = backend.expectOne('/api/turnos/4/cerrar');
    expect(r.request.body.contadoEfectivo).toBe(79000);
    expect(r.request.body.contadoOtros.TARJETA_DEBITO).toBe(0);
    r.flush({ ...abierto, estado: 'CERRADO', esperado: { EFECTIVO: 80000 }, contado: { EFECTIVO: 79000 }, diferencias: { EFECTIVO: -1000 }, requiereAprobacion: false });
    fixture.detectChanges();
    expect(texto()).toContain('Esperado');
    expect(texto()).toContain('cuadró dentro de la tolerancia');
  });
});
