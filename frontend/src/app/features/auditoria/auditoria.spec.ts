import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { RegistroAuditoria, Pagina } from '../../core/api/modelos';
import { Auditoria } from './auditoria';
import { ACCIONES, ENTIDADES, LISTA_ACCIONES, LISTA_ENTIDADES } from './textos';

const registro = (extra: Partial<RegistroAuditoria> = {}): RegistroAuditoria => ({
  id: 1, usuarioId: 3, usuarioNombre: 'Ana Compras', usuarioCorreo: 'ana@x.co', entidad: 'ORDEN_COMPRA', entidadId: 118, accion: 'ORDEN_CANCELAR',
  resultado: 'EXITO', ip: '10.0.0.5', detalle: 'motivo=El proveedor no cumple', creadoEn: '2026-11-18T09:30:00', ...extra,
});
const pagina = (lista: RegistroAuditoria[]): Pagina<RegistroAuditoria> => ({ contenido: lista, pagina: 0, tamano: 25, totalElementos: lista.length, totalPaginas: 1 });

describe('Auditoría de la empresa', () => {
  let fixture: ComponentFixture<Auditoria>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Auditoria);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/usuarios').flush({ contenido: [{ id: 3, nombreCompleto: 'Ana Compras' }], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne(r => r.url === '/api/auditoria').flush(pagina([registro(), registro({ id: 2, accion: 'LOGIN', entidad: 'USUARIO', entidadId: 3, resultado: 'FALLO', usuarioNombre: undefined, usuarioCorreo: undefined })]));
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('muestra cada registro con la acción en español y marca los fallidos', () => {
    expect(texto()).toContain('Canceló una orden');
    expect(texto()).toContain('Ana Compras');
    expect(texto()).toContain('Sin usuario');
    expect(texto()).toContain('Fallido');
    expect(fixture.nativeElement.querySelectorAll('tr.fallo').length).toBe(1);
  });

  it('abre el detalle con el código, la IP, el contexto y un enlace a la orden', () => {
    c()['alternar'](1);
    fixture.detectChanges();

    expect(texto()).toContain('ORDEN_CANCELAR');
    expect(texto()).toContain('motivo=El proveedor no cumple');
    expect(texto()).toContain('10.0.0.5');
    expect(fixture.nativeElement.querySelector('tr.detalle a')?.getAttribute('href')).toBe('/app/ordenes/118');

    c()['alternar'](1);
    fixture.detectChanges();
    expect(texto()).not.toContain('Código de la acción');
  });

  it('los filtros se mandan con las fechas completas y los vacíos no viajan', () => {
    c()['usuarioId'].set(3);
    c()['accion'].set('PAGO_ANULAR');
    c()['entidad'].set('PAGO');
    c()['resultado'].set('FALLO');
    c()['desde'].set('2026-11-01');
    c()['hasta'].set('2026-11-17');
    c()['buscar'](0);

    const p = backend.expectOne(r => r.url === '/api/auditoria');
    expect(p.request.params.get('usuarioId')).toBe('3');
    expect(p.request.params.get('accion')).toBe('PAGO_ANULAR');
    expect(p.request.params.get('entidad')).toBe('PAGO');
    expect(p.request.params.get('resultado')).toBe('FALLO');
    expect(p.request.params.get('desde')).toBe('2026-11-01T00:00:00');
    expect(p.request.params.get('hasta')).toBe('2026-11-17T23:59:59');
    expect(p.request.params.has('entidadId')).toBe(false);
    expect(p.request.params.get('sort')).toBe('creadoEn,desc');
    p.flush(pagina([]));
  });

  it('un rango de fechas al revés no sale al servidor y "limpiar" quita todo', () => {
    c()['desde'].set('2026-11-17');
    c()['hasta'].set('2026-11-01');
    c()['buscar'](0);
    backend.expectNone(r => r.url === '/api/auditoria');

    c()['limpiar']();
    const p = backend.expectOne(r => r.url === '/api/auditoria');
    expect(p.request.params.has('desde')).toBe(false);
    expect(c()['hayFiltros']()).toBe(false);
    p.flush(pagina([]));
  });

  it('los períodos rápidos arman el rango hasta hoy', () => {
    c()['rapido'](6);
    const p = backend.expectOne(r => r.url === '/api/auditoria');
    const desde = new Date(p.request.params.get('desde')!.slice(0, 10) + 'T00:00:00');
    const hasta = new Date(p.request.params.get('hasta')!.slice(0, 10) + 'T00:00:00');

    expect(Math.round((hasta.getTime() - desde.getTime()) / 86_400_000)).toBe(6);
    p.flush(pagina([]));
  });

  it('una acción que el frontend aún no conoce se muestra con su código en vez de quedar en blanco', () => {
    expect(c()['textoAccion'](registro({ accion: 'ACCION_NUEVA' }))).toBe('ACCION_NUEVA');
    expect(c()['textoEntidad'](registro({ entidad: 'ENTIDAD_NUEVA' }))).toBe('ENTIDAD_NUEVA');
    expect(c()['enlace'](registro({ entidad: 'PAGO' }))).toBeNull();
  });
});

describe('Textos de la auditoría', () => {
  it('todas las acciones y entidades tienen un texto y los selectores salen ordenados', () => {
    expect(Object.values(ACCIONES).every(t => t.trim().length > 3)).toBe(true);
    expect(Object.values(ENTIDADES).every(t => t.trim().length > 2)).toBe(true);
    expect(LISTA_ACCIONES.length).toBe(Object.keys(ACCIONES).length);
    expect(LISTA_ENTIDADES.map(e => ENTIDADES[e])).toEqual([...LISTA_ENTIDADES.map(e => ENTIDADES[e]!)].sort((a, b) => a!.localeCompare(b!, 'es')));
  });
});
