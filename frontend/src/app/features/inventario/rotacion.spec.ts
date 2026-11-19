import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { ItemRotacion, Rotacion } from '../../core/api/modelos';
import { RotacionProductos } from './rotacion';

const item = (extra: Partial<ItemRotacion>): ItemRotacion => ({
  productoId: 1, codigo: 'X', nombre: 'X', categoria: 'Ferretería', unidad: 'und', stock: 10, stockMinimo: 5, salidas: 0, comprado: 0,
  consumoDiario: 0, diasCobertura: undefined, valorStock: 1000, clase: 'SIN_MOVIMIENTO', porAgotarse: false, ...extra,
});

const datos: Rotacion = {
  desde: '2026-08-21', hasta: '2026-11-18', dias: 90,
  resumen: { productos: 5, conSalidas: 3, sinMovimiento: 1, agotados: 1, porAgotarse: 1, valorInventario: 500000, valorSinMovimiento: 270000,
    diasRotacionAlta: 30, diasRotacionBaja: 90, diasPorAgotarse: 15 },
  items: [
    item({ productoId: 1, codigo: 'TOR-01', nombre: 'Tornillo', salidas: 500, stock: 500, diasCobertura: 90, clase: 'MEDIA' }),
    item({ productoId: 2, codigo: 'CEM-50', nombre: 'Cemento', salidas: 280, stock: 20, diasCobertura: 6.4, clase: 'ALTA', porAgotarse: true,
      fechaEstimadaAgotamiento: '2026-11-24', consumoDiario: 3.1111 }),
    item({ productoId: 3, codigo: 'BRK-20', nombre: 'Breaker', salidas: 10, stock: 0, diasCobertura: 0, clase: 'AGOTADO' }),
    item({ productoId: 4, codigo: 'BRO-03', nombre: 'Brocha', salidas: 0, stock: 30, valorStock: 270000, clase: 'SIN_MOVIMIENTO', diasSinSalida: undefined }),
    item({ productoId: 5, codigo: 'CAB-12', nombre: 'Cable', salidas: 0, stock: 0, valorStock: 0, clase: 'SIN_ACTIVIDAD' }),
  ],
};

describe('Rotación de productos', () => {
  let fixture: ComponentFixture<RotacionProductos>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(RotacionProductos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/categorias').flush([{ id: 1, nombre: 'Ferretería', activo: true }]);
    backend.expectOne(r => r.url === '/api/reportes/rotacion').flush(datos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/sedes').flush([]);   // selector de sede (con una sola no se muestra)
  });

  afterEach(() => backend.verify());

  it('arranca con los últimos 90 días y muestra los totales que calcula el backend', () => {
    expect(texto()).toContain('90 días');
    expect(texto()).toContain('Por agotarse');
    expect(texto()).toContain('les alcanza 15 días o menos');
    expect(texto()).toContain('$ 270.000');
    expect(texto()).toContain('de $ 500.000 en inventario');
  });

  it('lista los que más salen de mayor a menor y los que se acaban pronto con su fecha', () => {
    expect(c()['masSalen']().map((i: ItemRotacion) => i.nombre)).toEqual(['Tornillo', 'Cemento', 'Breaker']);
    expect(c()['porAgotarse']().map((i: ItemRotacion) => i.nombre)).toEqual(['Cemento']);
    expect(texto()).toContain('24 nov 2026');
    expect(texto()).toContain('le alcanza 6 días');
    expect(texto()).toContain('ya se agotó');
  });

  it('separa lo que no rota: solo con stock y sin salidas, con su dinero parado', () => {
    expect(c()['sinMovimiento']().map((i: ItemRotacion) => i.nombre)).toEqual(['Brocha']);
    expect(texto()).toContain('nunca ha salido');
  });

  it('con pocos productos con salidas no repite a los de arriba en los que menos salen', () => {
    expect(c()['menosSalen']()).toEqual([]);
    expect(texto()).toContain('ya aparecen todos');
  });

  it('cambiar de período o de categoría vuelve a pedir el reporte con las fechas y la categoría', () => {
    c()['elegirPeriodo'](30);
    const p = backend.expectOne(r => r.url === '/api/reportes/rotacion');
    expect(p.request.params.has('desde')).toBe(true);
    expect(p.request.params.has('hasta')).toBe(true);
    expect(p.request.params.has('categoriaId')).toBe(false);
    p.flush(datos);

    c()['categoriaId'].set(1);
    c()['cargar']();
    const q = backend.expectOne(r => r.url === '/api/reportes/rotacion');
    expect(q.request.params.get('categoriaId')).toBe('1');
    q.flush(datos);
  });

  it('otras fechas sin escribir las dos no sale al servidor', () => {
    c()['elegirPeriodo']('otro');
    c()['cargar']();

    backend.expectNone(r => r.url === '/api/reportes/rotacion');
  });

  it('la tabla filtra por tipo de rotación y por texto, y ordena sin pedir de nuevo', () => {
    c()['claseFiltro'].set('SIN_MOVIMIENTO');
    expect(c()['filas']().map((i: ItemRotacion) => i.nombre)).toEqual(['Brocha']);

    c()['claseFiltro'].set(null);
    c()['texto'].set('ceme');
    expect(c()['filas']().map((i: ItemRotacion) => i.nombre)).toEqual(['Cemento']);

    c()['texto'].set('');
    c()['ordenarPor']('cobertura');   // a menos días primero
    expect(c()['filas']().map((i: ItemRotacion) => i.nombre)[0]).toBe('Breaker');
  });
});
