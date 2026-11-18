import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Pagina, Movimiento } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Movimientos } from './movimientos';

const bodegas = [{ id: 1, nombre: 'Central', activo: true }, { id: 2, nombre: 'Sede Norte', activo: true }];
const existencias = [
  { productoId: 10, productoCodigo: 'CAB-12', productoNombre: 'Cable 12 AWG', unidad: 'm', bodegaId: 1, bodegaNombre: 'Central', cantidad: 120 },
  { productoId: 11, productoCodigo: 'TOR-01', productoNombre: 'Tornillo 1 pulgada', unidad: 'und', bodegaId: 2, bodegaNombre: 'Sede Norte', cantidad: 40 },
];
const movimiento: Movimiento = {
  id: 1, tipo: 'TRASLADO', productoId: 10, productoCodigo: 'CAB-12', productoNombre: 'Cable 12 AWG', bodegaOrigenId: 1, bodegaOrigenNombre: 'Central',
  bodegaDestinoId: 2, bodegaDestinoNombre: 'Sede Norte', cantidad: 5, stockResultante: 115, stockResultanteDestino: 5, creadoEn: '2026-11-17T10:00:00',
};
const pagina: Pagina<Movimiento> = { contenido: [movimiento], pagina: 0, tamano: 25, totalElementos: 1, totalPaginas: 1 };

describe('Movimientos de stock', () => {
  let fixture: ComponentFixture<Movimientos>;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    const sesion = TestBed.inject(SesionService);
    sesion.usuario.set({ id: 3, nombre: 'Bodega', correo: 'b@x.co', roles: ['ALMACENISTA'] });
    fixture = TestBed.createComponent(Movimientos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/bodegas').flush(bodegas);
    backend.expectOne(r => r.url === '/api/inventario/stock').flush(existencias);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const c = () => fixture.componentInstance as unknown as Record<string, any>;

  it('muestra las existencias por bodega y filtra por texto sin pedir de nuevo', () => {
    expect(texto()).toContain('Cable 12 AWG');
    expect(texto()).toContain('Sede Norte');

    c()['textoStock'].set('tornillo');
    fixture.detectChanges();

    expect(texto()).toContain('Tornillo 1 pulgada');
    expect(texto()).not.toContain('Cable 12 AWG');
  });

  it('filtrar por bodega vuelve a pedir el stock solo de esa bodega', () => {
    c()['filtrarBodegaStock'](2);

    const p = backend.expectOne(r => r.url === '/api/inventario/stock');
    expect(p.request.params.get('bodegaId')).toBe('2');
    p.flush([existencias[1]]);
  });

  it('el kardex manda solo los filtros llenos y las fechas con hora', () => {
    c()['abrirVista']('kardex');
    backend.expectOne(r => r.url === '/api/inventario/movimientos').flush(pagina);

    c()['bodegaKardex'].set(1);
    c()['tipoKardex'].set('TRASLADO');
    c()['desde'].set('2026-11-01');
    c()['hasta'].set('2026-11-17');
    c()['buscar'](0);

    const p = backend.expectOne(r => r.url === '/api/inventario/movimientos');
    expect(p.request.params.get('bodegaId')).toBe('1');
    expect(p.request.params.get('tipo')).toBe('TRASLADO');
    expect(p.request.params.get('desde')).toBe('2026-11-01T00:00:00');
    expect(p.request.params.get('hasta')).toBe('2026-11-17T23:59:59');
    expect(p.request.params.has('productoId')).toBe(false);
    p.flush(pagina);
    fixture.detectChanges();
    expect(texto()).toContain('Traslado entre bodegas');
  });

  it('exportar el kardex sin producto no sale al servidor', () => {
    c()['exportarExcel']();

    backend.expectNone(r => r.url.includes('/api/exportaciones/'));
  });

  it('un traslado necesita dos bodegas distintas antes de enviarse', () => {
    c()['abrirRegistro']();
    c()['cambiarTipo']('TRASLADO');
    c()['formProducto'].opciones.set([{ id: 10, codigo: 'CAB-12', nombre: 'Cable 12 AWG' }]);
    c()['formProducto'].texto.set('CAB-12 · Cable 12 AWG');
    c()['origen'].set(1);
    c()['destino'].set(1);
    c()['cantidad'].set(5);
    c()['registrar']();

    backend.expectNone('/api/inventario/movimientos');
  });

  it('un ajuste exige motivo y un traslado correcto manda origen y destino, sin motivo vacío', () => {
    c()['abrirRegistro']();
    c()['cambiarTipo']('AJUSTE_NEGATIVO');
    c()['formProducto'].opciones.set([{ id: 10, codigo: 'CAB-12', nombre: 'Cable 12 AWG' }]);
    c()['formProducto'].texto.set('CAB-12 · Cable 12 AWG');
    c()['origen'].set(1);
    c()['cantidad'].set(2);
    c()['motivo'].set('mal');
    c()['registrar']();
    backend.expectNone('/api/inventario/movimientos');

    c()['cambiarTipo']('TRASLADO');
    c()['origen'].set(1);
    c()['destino'].set(2);
    c()['motivo'].set('');
    c()['cantidad'].set(5);
    c()['registrar']();

    const p = backend.expectOne('/api/inventario/movimientos');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toEqual({ productoId: 10, tipo: 'TRASLADO', bodegaOrigenId: 1, bodegaDestinoId: 2, cantidad: 5, motivo: undefined });
    p.flush({ movimiento, stockTotalProducto: 120, bajoStockMinimo: true });
    backend.expectOne(r => r.url === '/api/inventario/stock').flush(existencias);
    fixture.detectChanges();

    expect(texto()).toContain('quedó por debajo del mínimo');
  });

  it('quien no es almacenista no ve el botón de registrar', () => {
    TestBed.inject(SesionService).usuario.set({ id: 4, nombre: 'Auditor', correo: 'a@x.co', roles: ['AUDITOR'] });
    fixture.detectChanges();

    expect(texto()).not.toContain('Registrar movimiento');
  });
});
