import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Contratos } from './contratos';
import { PesosConfig } from './pesos';
import { Politicas } from './politicas';
import { Presupuestos } from './presupuestos';

let backend: HttpTestingController;

function montar<T>(clase: Type<T>, roles: string[]): ComponentFixture<T> {
  TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
  backend = TestBed.inject(HttpTestingController);
  TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Usuario', correo: 'u@x.co', roles });
  return TestBed.createComponent(clase);
}

const c = (f: ComponentFixture<unknown>) => f.componentInstance as Record<string, any>;
const texto = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent ?? '';

afterEach(() => backend.verify());

describe('Políticas de aprobación', () => {
  const politica = (extra: object) => ({ id: 1, tipo: 'SOLICITUD', rolAprobador: 'APROBADOR', rolAprobadorNombre: 'Aprobador', montoDesde: 0, montoHasta: 1000000, activo: true, ...extra });

  it('agrupa por tipo, ordena por monto y muestra "Sin tope" en el último rango', () => {
    const f = montar(Politicas, ['ADMIN_COMPRAS']);
    f.detectChanges();
    backend.expectOne('/api/politicas-aprobacion').flush([
      politica({ id: 2, montoDesde: 1000000, montoHasta: null, rolAprobador: 'ADMIN_COMPRAS', rolAprobadorNombre: 'Jefe de compras' }), politica({}),
    ]);
    f.detectChanges();

    expect(texto(f)).toContain('Sin tope');
    expect(c(f)['de']('SOLICITUD').map((p: { id: number }) => p.id)).toEqual([1, 2]);
  });

  it('un rango donde "hasta" no supera a "desde" no sale al servidor; uno válido manda tipo, rol y montos', () => {
    const f = montar(Politicas, ['ADMIN_COMPRAS']);
    f.detectChanges();
    backend.expectOne('/api/politicas-aprobacion').flush([]);
    c(f)['nueva']('ADJUDICACION');
    c(f)['desde'].set(500);
    c(f)['hasta'].set(100);
    c(f)['guardar']();
    backend.expectNone('/api/politicas-aprobacion');

    c(f)['hasta'].set(null);
    c(f)['guardar']();
    const p = backend.expectOne('/api/politicas-aprobacion');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toEqual({ tipo: 'ADJUDICACION', rolAprobador: 'APROBADOR', montoDesde: 500, montoHasta: undefined });
    p.flush(politica({ id: 3 }));
    backend.expectOne('/api/politicas-aprobacion').flush([]);
  });

  it('desactivar pide confirmación y quien no es jefe de compras solo consulta', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const f = montar(Politicas, ['ADMIN_COMPRAS']);
    f.detectChanges();
    backend.expectOne('/api/politicas-aprobacion').flush([politica({})]);
    c(f)['alternar'](politica({}));
    backend.expectOne('/api/politicas-aprobacion/1/desactivar').flush(politica({ activo: false }));
    backend.expectOne('/api/politicas-aprobacion').flush([]);

    const g = TestBed.createComponent(Politicas);
    TestBed.inject(SesionService).usuario.set({ id: 4, nombre: 'Aud', correo: 'a@x.co', roles: ['AUDITOR'] });
    g.detectChanges();
    backend.expectOne('/api/politicas-aprobacion').flush([politica({})]);
    g.detectChanges();
    expect(texto(g)).toContain('Solo consulta');
    expect(texto(g)).not.toContain('Agregar rango');
  });
});

describe('Pesos de evaluación', () => {
  const pesos = { id: 1, vigenteDesde: '2026-10-01', pesos: [{ codigo: 'PRECIO', nombre: 'Precio', peso: 60 }, { codigo: 'PLAZO', nombre: 'Plazo', peso: 40 }] };

  it('no guarda si no suman 100 y manda todos los criterios cuando suman', () => {
    const f = montar(PesosConfig, ['ADMIN_COMPRAS']);
    f.detectChanges();
    backend.expectOne('/api/config-pesos').flush(pesos);
    f.detectChanges();
    c(f)['editar']();
    c(f)['cambiar']('PRECIO', 70);
    expect(c(f)['suma']()).toBe(110);
    c(f)['guardar']();
    backend.expectNone('/api/config-pesos');

    c(f)['cambiar']('PLAZO', 30);
    expect(c(f)['suma100']()).toBe(true);
    c(f)['guardar']();
    const p = backend.expectOne('/api/config-pesos');
    expect(p.request.body).toEqual({ pesos: { PRECIO: 70, PLAZO: 30 } });
    p.flush({ ...pesos, id: 2 });
  });
});

describe('Presupuestos', () => {
  const presupuesto = { id: 1, centroCostoId: 7, anio: 2026, mes: 11, monto: 5000000, comprometido: 4200000, disponible: 800000, usoPct: 84 };

  function listo(roles: string[] = ['ADMIN_COMPRAS']) {
    const f = montar(Presupuestos, roles);
    f.detectChanges();
    backend.expectOne('/api/centros-costo').flush([{ id: 7, codigo: 'OP', nombre: 'Operaciones', activo: true }]);
    backend.expectOne(r => r.url === '/api/presupuestos').flush([presupuesto]);
    f.detectChanges();
    return f;
  }

  it('muestra lo que calcula el backend y pinta en ámbar lo que va por encima del 80 %', () => {
    const f = listo();

    expect(texto(f)).toContain('Operaciones');
    expect(texto(f)).toContain('Noviembre 2026');
    expect(c(f)['tono'](presupuesto)).toBe('ambar');
    expect(c(f)['tono']({ usoPct: 120 })).toBe('rojo');
  });

  it('crear manda centro, año, mes y monto; editar manda solo el monto', () => {
    const f = listo();
    c(f)['nuevo']();
    c(f)['monto'].set(3000000);
    c(f)['guardar']();
    const crear = backend.expectOne('/api/presupuestos');
    expect(crear.request.method).toBe('POST');
    expect(crear.request.body.centroCostoId).toBe(7);
    expect(crear.request.body.monto).toBe(3000000);
    crear.flush(presupuesto);
    backend.expectOne(r => r.url === '/api/presupuestos').flush([presupuesto]);

    c(f)['editar'](presupuesto);
    c(f)['monto'].set(6000000);
    c(f)['guardar']();
    const editar = backend.expectOne('/api/presupuestos/1');
    expect(editar.request.method).toBe('PUT');
    expect(editar.request.body).toEqual({ monto: 6000000 });
    editar.flush(presupuesto);
    backend.expectOne(r => r.url === '/api/presupuestos').flush([presupuesto]);
  });

  it('quien no es jefe de compras no ve los botones de cambio', () => {
    const f = listo(['AUDITOR']);

    expect(texto(f)).not.toContain('Cambiar monto');
    expect(texto(f)).not.toContain('Nuevo presupuesto');
  });
});

describe('Contratos', () => {
  const resumen = { id: 4, numero: 'CT-001', proveedorRazonSocial: 'Eléctricos del Norte', vigenteDesde: '2026-10-01', vigenteHasta: '2027-03-31', estado: 'ACTIVO', vigente: true };
  const detalle = { ...resumen, precios: [{ id: 1, productoCodigo: 'CAB-12', productoNombre: 'Cable', unidad: 'm', precioUnitario: 2500, impuestoPct: 19, cantidadMaxima: 1000, consumido: 200, disponible: 800 }] };

  function listo() {
    const f = montar(Contratos, ['ADMIN_COMPRAS']);
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [{ id: 9, razonSocial: 'Eléctricos del Norte' }], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne(r => r.url === '/api/contratos').flush({ contenido: [resumen], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });
    f.detectChanges();
    return f;
  }

  it('abre el detalle con lo consumido y lo disponible de cada tope', () => {
    const f = listo();
    c(f)['abrir'](resumen);
    backend.expectOne('/api/contratos/4').flush(detalle);
    f.detectChanges();

    expect(texto(f)).toContain('CAB-12');
    expect(texto(f)).toContain('Consumido');
    expect(texto(f)).toContain('Anular');
  });

  it('un contrato sin precio, con productos repetidos o con la vigencia al revés no sale; uno válido manda todo', () => {
    const f = listo();
    c(f)['nuevo']();
    c(f)['proveedorId'].set(9);
    c(f)['numero'].set('CT-002');
    c(f)['desde'].set('2027-01-01');
    c(f)['hasta'].set('2026-12-01');
    c(f)['guardar']();
    backend.expectNone('/api/contratos');

    c(f)['hasta'].set('2027-06-30');
    const linea = c(f)['precios']()[0];
    linea.buscador.opciones.set([{ id: 10, codigo: 'CAB-12', nombre: 'Cable' }]);
    linea.buscador.texto.set('CAB-12 · Cable');
    c(f)['guardar']();                                  // sin precio
    backend.expectNone('/api/contratos');

    c(f)['cambiarPrecio'](0, 'precio', 2400);
    c(f)['cambiarPrecio'](0, 'tope', 500);
    c(f)['guardar']();
    const p = backend.expectOne('/api/contratos');
    expect(p.request.body).toEqual({ proveedorId: 9, numero: 'CT-002', descripcion: undefined, vigenteDesde: '2027-01-01', vigenteHasta: '2027-06-30',
      precios: [{ productoId: 10, precioUnitario: 2400, impuestoPct: 19, cantidadMaxima: 500 }] });
    p.flush(detalle);
    backend.expectOne(r => r.url === '/api/contratos').flush({ contenido: [resumen], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });
  });

  it('anular exige un motivo de 5 caracteres y cambiar la vigencia manda la fecha', () => {
    const f = listo();
    c(f)['abrir'](resumen);
    backend.expectOne('/api/contratos/4').flush(detalle);

    c(f)['motivo'].set('no');
    c(f)['anular']();
    backend.expectNone('/api/contratos/4/anular');
    c(f)['motivo'].set('Cambió sus precios');
    c(f)['anular']();
    const a = backend.expectOne('/api/contratos/4/anular');
    expect(a.request.body).toEqual({ motivo: 'Cambió sus precios' });
    a.flush({ ...detalle, estado: 'ANULADO' });
    backend.expectOne(r => r.url === '/api/contratos').flush({ contenido: [resumen], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });

    c(f)['nuevaVigencia'].set('2027-09-30');
    c(f)['guardarVigencia']();
    const v = backend.expectOne('/api/contratos/4/vigencia');
    expect(v.request.body).toEqual({ vigenteHasta: '2027-09-30' });
    v.flush(detalle);
    backend.expectOne(r => r.url === '/api/contratos').flush({ contenido: [resumen], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 });
  });
});
