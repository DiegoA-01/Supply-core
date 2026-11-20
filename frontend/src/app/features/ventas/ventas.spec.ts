import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { VentaResumen } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PedidosVenta } from '../pos/pedidos-venta';
import { Ventas } from './ventas';

const resumen: VentaResumen = { id: 5, consecutivo: 'C1-000005', estado: 'CONFIRMADA', caja: 'Caja 1', cliente: 'Consumidor final', cajero: 'Pedro', totalAPagar: 18500 };
const pagina = <T>(l: T[]) => ({ contenido: l, pagina: 0, tamano: 20, totalElementos: l.length, totalPaginas: 1 });
const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null) } });
const limites = [{ rol: 'CAJERO', descuentoMaximoPct: 5 }, { rol: 'SUPERVISOR_VENTAS', descuentoMaximoPct: 15 }, { rol: 'VENDEDOR', descuentoMaximoPct: 5 }];

describe('Ventas', () => {
  let fixture: ComponentFixture<Ventas>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** @param roles roles de la sesión */
  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Ventas);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/ventas').flush(pagina([resumen]));
    if (roles.some(r => ['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS', 'AUDITOR'].includes(r))) {
      backend.expectOne('/api/ventas/limites-descuento').flush(limites);
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el historial muestra las ventas y el detalle con quién autorizó', () => {
    crear(['SUPERVISOR_VENTAS']);
    expect(texto()).toContain('C1-000005');
    c()['ver'](resumen);
    backend.expectOne('/api/ventas/5').flush({ ...resumen, autorizadaPor: 'Ana Supervisora', subtotal: 18500, lineas: [{ nombre: 'Cable', cantidad: 5, totalLinea: 18500 }], pagos: [] });
    fixture.detectChanges();
    expect(texto()).toContain('Ana Supervisora');
    // el supervisor ve los límites pero no los cambia
    expect(c()['esDueno']()).toBe(false);
  });

  it('el dueño guarda los límites de descuento', () => {
    crear(['ADMIN_SISTEMA']);
    c()['cambiarLimite']('CAJERO', 8);
    c()['guardarLimites']();
    const r = backend.expectOne(req => req.url === '/api/ventas/limites-descuento' && req.method === 'PUT');
    expect(r.request.body[0]).toEqual({ rol: 'CAJERO', descuentoMaximoPct: 8 });
    r.flush(limites);
  });

  it('los filtros viajan al servidor', () => {
    crear(['AUDITOR']);
    c()['consecutivo'].set(' c1-000005 ');
    c()['estado'].set('CONFIRMADA');
    c()['buscar']();
    const r = backend.expectOne(req => req.url === '/api/ventas');
    expect(r.request.params.get('consecutivo')).toBe('c1-000005');
    expect(r.request.params.get('estado')).toBe('CONFIRMADA');
    r.flush(pagina([]));
  });
});

describe('Pedidos de venta', () => {
  let fixture: ComponentFixture<PedidosVenta>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;

  it('el cajero ve los pendientes y descarta uno', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(['CAJERO'])] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PedidosVenta);
    fixture.detectChanges();
    const lista = backend.expectOne(r => r.url === '/api/pedidos-venta');
    expect(lista.request.params.get('estado')).toBe('PENDIENTE_COBRO');
    lista.flush(pagina([{ id: 3, consecutivo: 'PV-000003', estado: 'PENDIENTE_COBRO', lineas: [] }]));
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Cobrar');
    c()['descartar']({ id: 3, consecutivo: 'PV-000003' });
    backend.expectOne('/api/pedidos-venta/3/descartar').flush({ id: 3, estado: 'DESCARTADO' });
    backend.expectOne(r => r.url === '/api/pedidos-venta').flush(pagina([]));
    backend.verify();
  });
});
