import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PrecioFila, Precios } from '../../core/api/modelos';
import { PreciosMargen } from './precios';

const fila = (extra: Partial<PrecioFila>): PrecioFila => ({
  productoId: 1, codigo: 'CAB-012', nombre: 'Cable', categoria: 'Eléctricos', unidad: 'M', vendible: true, tarifaImpuestoId: 1,
  impuesto: 'IVA 19 %', porcentajeImpuesto: 19, precioIncluyeImpuesto: true, costo: 7000, precio: 11900, base: 10000,
  valorImpuesto: 1900, margen: 3000, margenPct: 30, stock: 10, alertas: [], ...extra,
});

const datos: Precios = {
  lista: { id: 5, nombre: 'General', esGeneral: true, activo: true }, productos: 2, conPrecio: 1, sinPrecio: 1, bajoCosto: 0,
  margenPromedioPct: 30, margenPonderadoPct: 30, margenInventario: 30000, puedeEditar: true,
  filas: [fila({}), fila({ productoId: 2, codigo: 'TOR-001', nombre: 'Tornillo', precio: undefined, base: undefined, margen: undefined,
    margenPct: undefined, alertas: ['SIN_PRECIO'] })],
};

describe('Precios y margen', () => {
  let fixture: ComponentFixture<PreciosMargen>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PreciosMargen);
    fixture.detectChanges();
    backend.expectOne('/api/ventas/listas').flush([datos.lista]);
    backend.expectOne('/api/ventas/impuestos').flush([{ id: 1, codigo: 'IVA19', nombre: 'IVA 19 %', porcentaje: 19, activo: true }]);
    backend.expectOne(r => r.url === '/api/ventas/precios').flush(datos);
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('muestra el margen que calcula el backend y los productos sin precio', () => {
    expect(texto()).toContain('Margen global');
    expect(texto()).toContain('30 %');
    expect(texto()).toContain('Sin precio');
    c()['filtro'].set('SIN_PRECIO');
    expect(c()['filas']().map((p: PrecioFila) => p.codigo)).toEqual(['TOR-001']);
  });

  it('al cambiar el precio manda solo el precio si los datos de venta no cambiaron', () => {
    c()['editar'](datos.filas![0]);
    c()['cambiar']('precio', 12500);
    c()['cambiar']('motivo', 'alza');
    c()['guardar'](datos.filas![0]);
    const req = backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'PUT');
    expect(req.request.body).toEqual({ listaId: 5, productoId: 1, precio: 12500, precioMinimo: null, motivo: 'alza' });
    req.flush(fila({ precio: 12500 }));
    backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'GET').flush(datos);
  });

  it('no deja un mínimo mayor que el precio', () => {
    c()['editar'](datos.filas![0]);
    c()['cambiar']('minimo', 20000);
    c()['guardar'](datos.filas![0]);
    backend.expectNone(r => r.method === 'PUT');
  });
});
