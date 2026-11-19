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
  valorImpuesto: 1900, margen: 3000, margenPct: 30, stock: 10, alertas: [], otrasUnidades: [], ...extra,
});

const unidades = [
  { id: 1, codigo: 'M', nombre: 'Metro', magnitud: 'LONGITUD', factorConversionBase: 1, activo: true },
  { id: 2, codigo: 'ROLLO100', nombre: 'Rollo de 100 m', magnitud: 'LONGITUD', factorConversionBase: 100, activo: true },
  { id: 3, codigo: 'KG', nombre: 'Kilogramo', magnitud: 'MASA', factorConversionBase: 1, activo: true },
];

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
    backend.expectOne(r => r.url === '/api/unidades-medida').flush(unidades);
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
    expect(req.request.body).toEqual({ listaId: 5, productoId: 1, precio: 12500, precioMinimo: null, motivo: 'alza', unidadMedidaId: null });
    req.flush(fila({ precio: 12500 }));
    backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'GET').flush(datos);
  });

  it('ofrece solo unidades que se convierten a la del producto y fija el precio por rollo', () => {
    const conRollo = fila({ otrasUnidades: [{ unidadMedidaId: 2, unidad: 'ROLLO100', precio: 1100000, precioMinimo: 1000000, margenPct: 28 }] });
    expect(c()['unidadesDe'](conRollo).map((u: { codigo: string }) => u.codigo)).toEqual(['ROLLO100']);   // KG mide otra cosa
    c()['editar'](conRollo);
    c()['cambiarUnidad'](conRollo, 2);
    expect(c()['edicion']().precio).toBe(1100000);                // trae el precio vigente por rollo
    c()['cambiar']('precio', 1150000);
    c()['guardar'](conRollo);
    const req = backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'PUT');
    expect(req.request.body).toEqual({ listaId: 5, productoId: 1, precio: 1150000, precioMinimo: 1000000, motivo: null, unidadMedidaId: 2 });
    req.flush(conRollo);
    backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'GET').flush(datos);
  });

  it('el Excel se revisa primero y solo se aplica si no tiene errores', () => {
    const archivo = new File(['x'], 'precios.xlsx');
    c()['elegirArchivo']({ target: { files: [archivo], value: 'precios.xlsx' } });
    const revision = backend.expectOne(r => r.url === '/api/ventas/precios/carga');
    expect(revision.request.params.get('soloValidar')).toBe('true');
    revision.flush({ soloValidar: true, aplicado: false, filas: 2, precios: 2, errores: [] });
    fixture.detectChanges();
    expect(texto()).toContain('se fijarán 2 precios');

    c()['subir'](false);
    const aplicar = backend.expectOne(r => r.url === '/api/ventas/precios/carga');
    expect(aplicar.request.params.get('soloValidar')).toBe('false');
    aplicar.flush({ soloValidar: false, aplicado: true, filas: 2, precios: 2, errores: [] });
    backend.expectOne(r => r.url === '/api/ventas/precios' && r.method === 'GET').flush(datos);
  });

  it('con errores en el Excel muestra las filas y no ofrece aplicar', () => {
    c()['elegirArchivo']({ target: { files: [new File(['x'], 'precios.xlsx')], value: '' } });
    backend.expectOne(r => r.url === '/api/ventas/precios/carga')
      .flush({ soloValidar: true, aplicado: false, filas: 2, precios: 1, errores: [{ fila: 3, mensaje: 'el producto NO-EXISTE no existe' }] });
    fixture.detectChanges();
    expect(texto()).toContain('No se cambió ningún precio');
    expect(texto()).toContain('Fila 3: el producto NO-EXISTE no existe');
    expect(texto()).not.toContain('Fijar los precios');
  });

  it('no deja un mínimo mayor que el precio', () => {
    c()['editar'](datos.filas![0]);
    c()['cambiar']('minimo', 20000);
    c()['guardar'](datos.filas![0]);
    backend.expectNone(r => r.method === 'PUT');
  });
});
