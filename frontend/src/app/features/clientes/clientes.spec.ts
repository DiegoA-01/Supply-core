import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Cliente } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { ClienteFormulario } from './cliente-formulario';
import { Clientes } from './clientes';

const consumidor: Cliente = { id: 1, tipoDocumento: 'CC', numeroDocumento: '222222222222', nombre: 'Consumidor final',
  cupoCredito: 0, plazoDias: 0, esConsumidorFinal: true, activo: true };
const ana: Cliente = { id: 2, tipoDocumento: 'CC', numeroDocumento: '1020304050', nombre: 'Ana Pérez', telefono: '3001234567',
  aceptaDatosEn: '2026-11-19T10:00:00', listaPrecio: 'Mayorista', listaPrecioId: 7, cupoCredito: 500000, plazoDias: 30,
  esConsumidorFinal: false, activo: true };
const pagina = (l: Cliente[]) => ({ contenido: l, pagina: 0, tamano: 20, totalElementos: l.length, totalPaginas: 1 });

const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null) } });

describe('Clientes', () => {
  let fixture: ComponentFixture<Clientes>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Clientes);
    fixture.detectChanges();
    const req = backend.expectOne(r => r.url === '/api/clientes');
    expect(req.request.params.get('activo')).toBe('true');
    req.flush(pagina([consumidor, ana]));
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el cajero ve los clientes sin cupo y no puede editar al consumidor final', () => {
    crear(['CAJERO']);
    expect(texto()).toContain('Consumidor final');
    expect(texto()).toContain('Ana Pérez');
    expect(texto()).not.toContain('Cupo');
    expect(texto()).not.toContain('Exportar a Excel');
    const botones = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody button');
    expect(botones.length).toBe(1);                 // solo Ana: el consumidor final no se edita
    c()['editar'](consumidor);
    expect(c()['editando']()).toBeNull();
  });

  it('el supervisor ve cupo y lista, y carga las listas de precios para elegir', () => {
    crear(['SUPERVISOR_VENTAS']);
    backend.expectOne('/api/ventas/listas').flush([{ id: 7, nombre: 'Mayorista', esGeneral: false, activo: true }]);
    fixture.detectChanges();
    expect(texto()).toContain('Cupo');
    expect(texto()).toContain('Mayorista');
    expect(texto()).toContain('30 días');
  });

  it('la búsqueda manda el texto y vuelve a la primera página', () => {
    crear(['CAJERO']);
    c()['pagina'].set(3);
    c()['texto'].set(' 1020 ');
    c()['buscar']();
    const req = backend.expectOne(r => r.url === '/api/clientes');
    expect(req.request.params.get('q')).toBe('1020');
    expect(req.request.params.get('page')).toBe('0');
    req.flush(pagina([ana]));
  });
});

describe('Formulario de cliente', () => {
  let fixture: ComponentFixture<ClienteFormulario>;
  let backend: HttpTestingController;
  const f = () => fixture.componentInstance as unknown as Record<string, any>;

  const crear = (puedeCondiciones: boolean, cliente: Cliente | null = null) => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ClienteFormulario);
    fixture.componentRef.setInput('puedeCondiciones', puedeCondiciones);
    fixture.componentRef.setInput('cliente', cliente);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('sin autorización de datos no envía teléfono, correo ni dirección', () => {
    crear(false);
    f()['numero'].set('1020304050');
    f()['nombre'].set('Ana');
    f()['telefono'].set('3001234567');
    f()['guardar']();
    const req = backend.expectOne(r => r.url === '/api/clientes' && r.method === 'POST');
    expect(req.request.body.telefono).toBeNull();
    expect(req.request.body.aceptaDatos).toBe(false);
    req.flush({ ...ana });
  });

  it('el cajero no envía cupo, plazo ni lista aunque estén escritos', () => {
    crear(false);
    f()['numero'].set('1020304050');
    f()['nombre'].set('Ana');
    f()['acepta'].set(true);
    f()['telefono'].set(' 3001234567 ');
    f()['cupo'].set(900000);
    f()['guardar']();
    const req = backend.expectOne(r => r.method === 'POST');
    expect(req.request.body).toMatchObject({ telefono: '3001234567', aceptaDatos: true, cupoCredito: null, plazoDias: null, listaPrecioId: null });
    req.flush({ ...ana });
  });

  it('al editar con permiso de condiciones manda cupo y el estado', () => {
    crear(true, ana);
    expect(f()['acepta']()).toBe(true);
    f()['cupo'].set(800000);
    f()['activo'].set(false);
    f()['guardar']();
    const req = backend.expectOne(r => r.url === '/api/clientes/2' && r.method === 'PUT');
    expect(req.request.body).toMatchObject({ cupoCredito: 800000, plazoDias: 30, listaPrecioId: 7, activo: false });
    req.flush({ ...ana, cupoCredito: 800000, activo: false });
  });

  it('no envía si falta el número o el nombre', () => {
    crear(false);
    f()['nombre'].set('Ana');
    f()['guardar']();
    backend.expectNone(r => r.method === 'POST');
  });
});
