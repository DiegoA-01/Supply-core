import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { TableroVentas, VentaReporte } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { TableroDueno } from '../tablero/tablero';
import { ReportesVentas } from './reportes-ventas';

const reporte: VentaReporte = {
  desde: '2026-11-01', hasta: '2026-11-20', agrupar: 'PRODUCTO', soloPropio: false,
  filas: [{ clave: '7', nombre: 'CAB-12 - Cable 12', ventas: 2, unidades: 4, bruto: 50000, devoluciones: 10000, neto: 40000,
    baseNeta: 33613.45, costo: 24000, margen: 9613.45, margenPct: 28.6, baseSinCosto: 0 }],
  totales: { ventas: 2, bruto: 50000, devoluciones: 10000, redondeo: 0, neto: 40000, baseNeta: 33613.45, costo: 24000,
    margen: 9613.45, margenPct: 28.6, baseSinCosto: 0, ticketPromedio: 20000 },
};
const sesion = (roles: string[], modulos = ['VENTAS', 'REPORTES']) => ({ provide: SesionService, useValue: {
  tieneRol: (r: string[]) => r.some(x => roles.includes(x)), tieneModulo: (m: string) => modulos.includes(m),
  soloLectura: () => false, empresa: signal(null) } });

describe('Reportes de ventas', () => {
  let fixture: ComponentFixture<ReportesVentas>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportesVentas);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra las cifras del servidor tal como llegan y pide la agrupación elegida', () => {
    crear(['ADMIN_SISTEMA']);
    backend.expectOne(r => r.url === '/api/reportes/ventas' && r.params.get('agrupar') === 'DIA').flush({ ...reporte, agrupar: 'DIA' });
    c()['agrupar'].set('PRODUCTO');
    c()['consultar']();
    const req = backend.expectOne(r => r.url === '/api/reportes/ventas');
    expect(req.request.params.get('agrupar')).toBe('PRODUCTO');
    expect(req.request.params.get('desde')).toMatch(/^\d{4}-\d{2}-01$/);
    req.flush(reporte);
    fixture.detectChanges();
    expect(texto()).toContain('CAB-12 - Cable 12');
    expect(texto()).toContain('40.000');
    expect(texto()).toContain('28,6 %');
    expect(texto()).toContain('Caja');
    expect(texto()).toContain('Cuadre diario');
  });

  it('el cajero solo ve la pestaña de ventas y el aviso de que son las suyas', () => {
    crear(['CAJERO']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush({ ...reporte, soloPropio: true });
    fixture.detectChanges();
    expect(texto()).toContain('Solo se muestran tus ventas');
    expect(texto()).not.toContain('Cuadre diario');
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('[role=tab]').length).toBe(1);
  });

  it('el dueño ve los días que no cuadran y puede revisarlos otra vez', () => {
    crear(['ADMIN_SISTEMA']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['elegir']('cuadre');
    const lista = backend.expectOne(r => r.url === '/api/reportes/cuadre');
    expect(lista.request.params.get('soloDescuadres')).toBe('true');
    lista.flush({ contenido: [{ fecha: '2026-11-19', cuadra: false, ventasConfirmadas: 60000, ventasEnCaja: 59000,
      unidadesVendidas: 6, unidadesSalida: 6, turnosAbiertos24h: 0,
      ventas: [{ ventaId: 1, consecutivo: 'R1-000001', cobrado: 20000, enCaja: 19000, vendidas: 2, salidas: 2 }], turnos: [] }],
    pagina: 0, tamano: 31, totalElementos: 1, totalPaginas: 1 });
    fixture.detectChanges();
    expect(texto()).toContain('No cuadra');
    expect(texto()).toContain('R1-000001');
    c()['revisar']('2026-11-19');
    backend.expectOne(r => r.url === '/api/reportes/cuadre/2026-11-19' && r.method === 'POST').flush({ fecha: '2026-11-19', cuadra: true });
    backend.expectOne(r => r.url === '/api/reportes/cuadre').flush({ contenido: [], pagina: 0, tamano: 31, totalElementos: 0, totalPaginas: 0 });
  });

  it('exporta con los mismos filtros de la pantalla', () => {
    crear(['SUPERVISOR_VENTAS']);
    backend.expectOne(r => r.url === '/api/reportes/ventas').flush(reporte);
    c()['agrupar'].set('MEDIO_PAGO');
    c()['exportar']('pdf');
    const req = backend.expectOne(r => r.url === '/api/exportaciones/reportes/ventas');
    expect(req.request.params.get('formato')).toBe('pdf');
    expect(req.request.params.get('agrupar')).toBe('MEDIO_PAGO');
    req.flush(new Blob(['%PDF']));
  });
});

describe('Bloque de ventas del tablero', () => {
  let backend: HttpTestingController;
  const ventas: TableroVentas = { hoy: '2026-11-20', desdeMes: '2026-11-01', ventasHoy: 40000, numeroVentasHoy: 2,
    ventasMes: 1250000, numeroVentasMes: 61, ticketPromedioMes: 20491.8, margenMes: 300000, margenMesPct: 28,
    masVendidos: [{ clave: '7', nombre: 'CAB-12 - Cable 12', neto: 400000 }], porMedio: [{ clave: 'EFECTIVO', nombre: 'Efectivo', neto: 900000 }],
    porCajero: [{ clave: '3', nombre: 'Pedro Cajero', neto: 1250000 }], diferenciaCajaMes: -5000, faltantesMes: -5000, turnosPorAprobar: 1 };

  const crear = (modulos: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      sesion(['ADMIN_SISTEMA'], modulos)] });
    backend = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(TableroDueno);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/reportes/tablero').flush(null);
    backend.match(r => r.url === '/api/sedes').forEach(r => r.flush([]));
    return fixture;
  };

  afterEach(() => backend.verify());

  it('con el módulo de ventas pide el bloque y lo muestra con las cifras del servidor', () => {
    const fixture = crear(['VENTAS', 'REPORTES']);
    backend.expectOne('/api/reportes/tablero/ventas').flush(ventas);
    expect((fixture.componentInstance as unknown as Record<string, any>)['ventas']()).toEqual(ventas);
  });

  it('sin el módulo de ventas no lo pide', () => {
    crear(['REPORTES']);
    backend.expectNone('/api/reportes/tablero/ventas');
  });
});
