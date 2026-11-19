import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Tablero } from '../../core/api/modelos';
import { TableroDueno } from './tablero';

const meses = Array.from({ length: 12 }, (_, i) => {
  const fecha = new Date(2025, 11 + i, 1);
  const periodo = `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}`;
  return { periodo, ordenes: 1, subtotal: 0, impuestos: 0, total: i === 11 ? 300000 : i === 10 ? 200000 : 0 };
});

const datos: Tablero = {
  hoy: '2026-11-18', desde: '2025-12-01', gastoDoceMeses: 500000, pagadoDoceMeses: 100000, porPagarDoceMeses: 400000,
  ordenesDoceMeses: 2, gastoMesActual: 300000, gastoMesAnterior: 200000, variacionMesPct: 50, gastoPorMes: meses,
  gastoPorCategoria: [{ id: 1, nombre: 'Tornillería', ordenes: 2, subtotal: 0, impuestos: 0, total: 500000 }],
  gastoPorProveedor: [{ id: 7, nombre: 'Aceros del Valle', ordenes: 2, subtotal: 0, impuestos: 0, total: 500000 }],
  valorInventario: 900000, valorSinMovimiento: 270000, productosActivos: 3, agotados: 0, bajoMinimo: 1, diasRotacion: 90,
  productosPorClase: { ALTA: 1, MEDIA: 1, BAJA: 0, SIN_MOVIMIENTO: 1, AGOTADO: 0, SIN_ACTIVIDAD: 0 },
  masMovidos: [{ productoId: 1, codigo: 'TOR-01', nombre: 'Tornillo', categoria: 'T', unidad: 'UND', stock: 5, stockMinimo: 0,
    salidas: 90, comprado: 0, consumoDiario: 1, clase: 'ALTA', porAgotarse: true }],
  menosMovidos: [{ productoId: 4, codigo: 'BRO-03', nombre: 'Brocha', categoria: 'T', unidad: 'UND', stock: 30, stockMinimo: 0,
    salidas: 0, comprado: 0, consumoDiario: 0, valorStock: 270000, clase: 'SIN_MOVIMIENTO', porAgotarse: false }],
  reabastecerUrgentes: 1, reabastecerPedirYa: 2, reabastecerPronto: 1,
  porPedir: [{ productoId: 1, codigo: 'TOR-01', nombre: 'Tornillo', categoria: 'T', unidad: 'UND', stock: 5, stockMinimo: 0,
    porRecibir: 0, enSolicitudes: 0, consumoDiario: 1, plazoEntregaDias: 6, plazoPropio: false, diasSeguridad: 2,
    entregasHistoricas: 0, llegadaSiPidoHoy: '2026-11-26', puntoPedido: 12, cantidadSugerida: 37, estado: 'URGENTE',
    motivo: 'Se acaba antes de que llegue.' }],
};

describe('Tablero del dueño', () => {
  let fixture: ComponentFixture<TableroDueno>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TableroDueno);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/reportes/tablero').flush(datos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/sedes').flush([]);   // selector de sede
  });

  afterEach(() => backend.verify());

  it('muestra las cifras que calcula el backend, sin recalcularlas', () => {
    expect(texto()).toContain('$ 300.000');
    expect(texto()).toContain('50 %');
    expect(texto()).toContain('$ 270.000');
    expect(texto()).toContain('Se acaba antes de que llegue.');
    expect(texto()).toContain('ventas, ingresos y márgenes');
  });

  it('dibuja doce barras con el mes actual resaltado y el más alto al 100 %', () => {
    const barras = c()['barras']();
    expect(barras.length).toBe(12);
    expect(barras[11].actual).toBe(true);
    expect(barras[11].alto).toBe(100);
    expect(barras[0].etiqueta).toBe('dic');
    expect(barras[1].etiqueta).toBe('ene 26');
  });

  it('al elegir una sede vuelve a pedir el tablero solo de esa sede', () => {
    c()['cambiarSede'](4);
    const req = backend.expectOne(r => r.url === '/api/reportes/tablero');
    expect(req.request.params.get('sedeId')).toBe('4');
    req.flush({ ...datos, sedeId: 4, gastoPorSede: [] });
    fixture.detectChanges();
    expect(texto()).toContain('Vista de una sede');
    expect(texto()).not.toContain('bajo el mínimo');
  });

  it('en toda la empresa compara las compras por sede cuando hay más de una', () => {
    c()['datos'].set({ ...datos, sedeId: undefined, gastoPorSede: [
      { id: 1, nombre: 'Principal', ordenes: 2, subtotal: 0, impuestos: 0, total: 400000 },
      { id: 2, nombre: 'Norte', ordenes: 1, subtotal: 0, impuestos: 0, total: 100000 },
    ] });
    fixture.detectChanges();
    expect(texto()).toContain('Compras por sede');
    expect(texto()).toContain('Norte');
  });

  it('reparte la barra de rotación por cantidad de productos', () => {
    expect(c()['totalClases']()).toBe(3);
    expect(Math.round(c()['parte']('ALTA'))).toBe(33);
  });
});
