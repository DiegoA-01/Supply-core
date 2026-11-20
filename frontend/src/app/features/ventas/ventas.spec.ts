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

  /** @param roles roles de la sesión; @param turno turno abierto de quien tiene caja */
  const crear = (roles: string[], turno: object | null = null) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Ventas);
    fixture.detectChanges();
    if (roles.some(r => r === 'CAJERO' || r === 'SUPERVISOR_VENTAS')) {
      const actual = backend.expectOne('/api/turnos/actual');
      if (turno) {
        actual.flush(turno);
      } else {
        actual.flush({}, { status: 404, statusText: 'Not Found' });
      }
    }
    if (!(roles.length === 1 && roles[0] === 'CAJERO')) {
      backend.expectOne(r => r.url === '/api/ventas').flush(pagina([resumen]));
    }
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

  it('el cajero no ve la lista: busca por consecutivo y anula en su turno con el PIN', () => {
    crear(['CAJERO'], { id: 4, estado: 'ABIERTO' });
    expect(texto()).toContain('Escriba el consecutivo');
    c()['buscar']();
    backend.expectNone(r => r.url === '/api/ventas');
    c()['consecutivo'].set('C1-000005');
    c()['buscar']();
    backend.expectOne(r => r.url === '/api/ventas' && r.params.get('consecutivo') === 'C1-000005').flush(pagina([resumen]));
    c()['ver'](resumen);
    const venta = { ...resumen, turnoId: 4, turnoAbierto: true, totalDevuelto: 0, lineas: [{ id: 11, nombre: 'Cable', cantidad: 5, cantidadDevuelta: 0, totalLinea: 18500 }], pagos: [] };
    backend.expectOne('/api/ventas/5').flush(venta);
    fixture.detectChanges();
    expect(c()['puedeAnular']()).toBe(true);
    c()['empezar']('anular');
    c()['motivo'].set('El cliente desistió');
    c()['autorizado']('permiso-7');
    const r = backend.expectOne('/api/ventas/5/anular');
    expect(r.request.body).toEqual({ motivo: 'El cliente desistió', autorizacion: 'permiso-7' });
    r.flush({ ...venta, estado: 'ANULADA', motivoAnulacion: 'El cliente desistió' });
    fixture.detectChanges();
    expect(texto()).toContain('Anulada');
    expect(c()['puedeAnular']()).toBe(false);
  });

  it('la devolución no deja pasar lo vendido y viaja con la línea, el estado y el reembolso', () => {
    crear(['CAJERO'], { id: 9, estado: 'ABIERTO' });
    c()['detalle'].set({ ...resumen, turnoId: 4, turnoAbierto: false, totalDevuelto: 0,
      lineas: [{ id: 11, nombre: 'Cable', cantidad: 5, cantidadDevuelta: 2, totalLinea: 18500 }], pagos: [] });
    expect(c()['puedeAnular']()).toBe(false);
    expect(c()['puedeDevolver']()).toBe(true);
    c()['empezar']('devolver');
    expect(c()['lineasDevueltas']()[0].maximo).toBe(3);
    c()['motivo'].set('No le sirvió');
    c()['cambiarLinea'](0, 'cantidad', 4);
    expect(c()['totalDevolucionValido']()).toBe(false);
    c()['cambiarLinea'](0, 'cantidad', 1);
    c()['cambiarLinea'](0, 'estado', 'DANADO');
    c()['forma'].set('SALDO_A_FAVOR');
    expect(c()['totalDevolucionValido']()).toBe(true);
    c()['autorizado']('permiso-8');
    const r = backend.expectOne('/api/devoluciones');
    expect(r.request.body).toEqual({ ventaId: 5, motivo: 'No le sirvió', formaReembolso: 'SALDO_A_FAVOR', autorizacion: 'permiso-8',
      lineas: [{ ventaLineaId: 11, cantidad: 1, estadoProducto: 'DANADO' }] });
    r.flush({ id: 1, consecutivo: 'DV-000001', totalReembolso: 3700 });
    backend.expectOne('/api/ventas/5').flush({ ...resumen, lineas: [], pagos: [] });
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
