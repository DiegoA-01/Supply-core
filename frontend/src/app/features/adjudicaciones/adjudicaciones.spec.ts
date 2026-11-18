import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Adjudicacion, Delegacion, Pagina } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Adjudicaciones } from './adjudicaciones';
import { Delegaciones } from './delegaciones';

const adjudicacion = (extra: Partial<Adjudicacion> = {}): Adjudicacion => ({
  id: 5, rfqId: 12, rfqConsecutivo: 'RFQ-000012', proveedorRazonSocial: 'Eléctricos del Norte', montoTotal: 8500000, sigueRecomendacion: true,
  estado: 'PENDIENTE_APROBACION', usuarioId: 20, decididoEn: '2026-11-18T09:00:00', ...extra,
});
const pagina = (lista: Adjudicacion[]): Pagina<Adjudicacion> => ({ contenido: lista, pagina: 0, tamano: 20, totalElementos: lista.length, totalPaginas: 1 });

describe('Adjudicaciones por aprobar', () => {
  let fixture: ComponentFixture<Adjudicaciones>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 99, nombre: 'Aprobador', correo: 'a@x.co', roles: ['APROBADOR'] });
    fixture = TestBed.createComponent(Adjudicaciones);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/adjudicaciones/pendientes').flush(pagina([adjudicacion(), adjudicacion({ id: 6, usuarioId: 99, rfqConsecutivo: 'RFQ-000013' })]));
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('lista las pendientes y al abrir una muestra qué rol la aprueba', () => {
    expect(texto()).toContain('RFQ-000012');
    c()['abrir'](adjudicacion());
    const p = backend.expectOne(r => r.url === '/api/politicas-aprobacion/aplicable');
    expect(p.request.params.get('tipo')).toBe('ADJUDICACION');
    expect(p.request.params.get('monto')).toBe('8500000');
    p.flush({ requiereAprobacion: true, politica: { id: 1, rolAprobador: 'ADMIN_COMPRAS', rolAprobadorNombre: 'Jefe de compras' } });
    fixture.detectChanges();

    expect(texto()).toContain('Jefe de compras');
    expect(texto()).toContain('Aprobar');
  });

  it('rechazar exige un motivo; aprobar no', () => {
    c()['abrir'](adjudicacion());
    backend.expectOne(r => r.url === '/api/politicas-aprobacion/aplicable').flush({ requiereAprobacion: true });
    c()['decidir'](false);
    backend.expectNone('/api/adjudicaciones/5/decision');

    c()['observaciones'].set('Precio por encima del contrato');
    c()['decidir'](false);
    const rechazo = backend.expectOne('/api/adjudicaciones/5/decision');
    expect(rechazo.request.body).toEqual({ aprobar: false, observaciones: 'Precio por encima del contrato' });
    rechazo.flush(adjudicacion({ estado: 'RECHAZADA' }));
    backend.expectOne(r => r.url === '/api/adjudicaciones/pendientes').flush(pagina([]));
  });

  it('aprobar manda la decisión sin observaciones y recarga la bandeja', () => {
    c()['abrir'](adjudicacion());
    backend.expectOne(r => r.url === '/api/politicas-aprobacion/aplicable').flush({ requiereAprobacion: true });
    c()['decidir'](true);

    const p = backend.expectOne('/api/adjudicaciones/5/decision');
    expect(p.request.body).toEqual({ aprobar: true, observaciones: undefined });
    p.flush(adjudicacion({ estado: 'APROBADA' }));
    backend.expectOne(r => r.url === '/api/adjudicaciones/pendientes').flush(pagina([]));
    fixture.detectChanges();

    expect(texto()).toContain('aprobada');
  });

  it('no deja aprobar la adjudicación que hizo uno mismo (segregación de funciones)', () => {
    c()['abrir'](adjudicacion({ id: 6, usuarioId: 99 }));
    backend.expectOne(r => r.url === '/api/politicas-aprobacion/aplicable').flush({ requiereAprobacion: true });
    fixture.detectChanges();

    expect(texto()).toContain('segregación de funciones');
    expect(texto()).not.toContain('Observaciones (obligatorias');
  });
});

describe('Delegaciones', () => {
  let fixture: ComponentFixture<Delegaciones>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const delegacion = (extra: Partial<Delegacion> = {}): Delegacion => ({
    id: 3, deleganteId: 20, deleganteNombre: 'Mario', delegadoId: 30, delegadoNombre: 'Sofía', vigenteDesde: '2026-11-18', vigenteHasta: '2026-11-30',
    estado: 'ACTIVA', vigente: true, motivo: 'Vacaciones', ...extra,
  });

  const crear = (candidatos: object[], error = false) => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 20, nombre: 'Mario', correo: 'm@x.co', roles: ['APROBADOR'] });
    fixture = TestBed.createComponent(Delegaciones);
    fixture.detectChanges();
    backend.expectOne('/api/delegaciones/otorgadas').flush([delegacion()]);
    backend.expectOne('/api/delegaciones/recibidas').flush([]);
    const cand = backend.expectOne('/api/delegaciones/candidatos');
    if (error) {
      cand.flush({ codigo: 'SIN_ROL_APROBADOR', mensaje: 'Solo quien tiene un rol que aprueba puede delegar' }, { status: 409, statusText: 'Conflict' });
    } else {
      cand.flush(candidatos);
    }
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra las que di y las que recibí, y permite revocar una activa', () => {
    crear([{ id: 30, nombre: 'Sofía' }]);

    expect(texto()).toContain('Sofía');
    expect(texto()).toContain('Revocar');
    expect(texto()).toContain('Nadie te ha delegado');
  });

  it('delegar manda el delegado, las fechas y el motivo; no sale con la vigencia al revés', () => {
    crear([{ id: 30, nombre: 'Sofía' }]);
    c()['nueva']();
    c()['delegadoId'].set(30);
    c()['desde'].set('2026-11-20');
    c()['hasta'].set('2026-11-10');
    c()['guardar']();
    backend.expectNone('/api/delegaciones');

    c()['hasta'].set('2026-11-30');
    c()['motivo'].set('Vacaciones');
    c()['guardar']();
    const p = backend.expectOne('/api/delegaciones');
    expect(p.request.body).toEqual({ delegadoId: 30, vigenteDesde: '2026-11-20', vigenteHasta: '2026-11-30', motivo: 'Vacaciones' });
    p.flush(delegacion());
    backend.expectOne('/api/delegaciones/otorgadas').flush([delegacion()]);
    backend.expectOne('/api/delegaciones/recibidas').flush([]);
  });

  it('quien no tiene un rol que aprueba ve el motivo y no el botón de delegar', () => {
    crear([], true);

    expect(texto()).toContain('Solo quien tiene un rol que aprueba puede delegar');
    expect(texto()).not.toContain('Delegar en');
  });
});
