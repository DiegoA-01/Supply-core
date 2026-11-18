import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Catalogo } from './catalogo';
import { Configuracion } from './configuracion';

describe('Catálogo simple', () => {
  let fixture: ComponentFixture<Catalogo>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (clave: string, roles: string[], filas: object[], extra: () => void = () => undefined) => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Admin', correo: 'a@x.co', roles });
    fixture = TestBed.createComponent(Catalogo);
    fixture.componentRef.setInput('catalogo', clave);
    fixture.detectChanges();
    extra();
    fixture.detectChanges();
    backend.expectOne(r => r.url === `/api/${clave}`).flush(filas);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('lista las bodegas con el nombre de su sede y pide las sedes para el formulario', () => {
    crear('bodegas', ['ADMIN_SISTEMA'], [{ id: 1, nombre: 'Central', direccion: 'Cra 1', sedeId: 2, sedeNombre: 'Sede Norte', activo: true }],
      () => backend.expectOne(r => r.url === '/api/sedes').flush([{ id: 2, nombre: 'Sede Norte', activo: true }]));

    expect(texto()).toContain('Central');
    expect(texto()).toContain('Sede Norte');
    expect(texto()).toContain('Desactivar');
  });

  it('crear manda el sede como número y omite los opcionales vacíos', () => {
    crear('bodegas', ['ADMIN_SISTEMA'], [], () => backend.expectOne(r => r.url === '/api/sedes').flush([{ id: 2, nombre: 'Sede Norte', activo: true }]));
    c()['nuevo']();
    c()['escribir'](c()['config']().campos[0], 'Bodega Sur');
    c()['escribir'](c()['config']().campos[2], '2');
    c()['guardar']();

    const p = backend.expectOne('/api/bodegas');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toEqual({ nombre: 'Bodega Sur', sedeId: 2 });
    p.flush({ id: 5, nombre: 'Bodega Sur', activo: true });
    backend.expectOne(r => r.url === '/api/bodegas').flush([]);
  });

  it('un campo obligatorio vacío no sale al servidor', () => {
    crear('categorias', ['ADMIN_COMPRAS'], []);
    c()['nuevo']();
    c()['guardar']();

    backend.expectNone('/api/categorias');
  });

  it('en una unidad de medida existente solo se cambia el nombre y lo demás va bloqueado', async () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Compras', correo: 'c@x.co', roles: ['ADMIN_COMPRAS'] });
    fixture = TestBed.createComponent(Catalogo);
    fixture.componentRef.setInput('catalogo', 'unidades-medida');
    fixture.detectChanges();
    // la pantalla pide la lista de la tabla y, aparte, la de unidades para el convertidor
    const pedidos = backend.match(r => r.url === '/api/unidades-medida');
    expect(pedidos.length).toBe(2);
    pedidos[0]!.flush([{ id: 5, codigo: 'KG', nombre: 'Kilo', magnitud: 'MASA', factorConversionBase: 1000, activo: true }]);
    pedidos[1]!.flush([]);
    fixture.detectChanges();

    c()['editar'](c()['filas']()[0]);
    c()['escribir'](c()['config']().campos[1], 'Kilogramo');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    const bloqueados = (fixture.nativeElement as HTMLElement).querySelectorAll('form input:disabled, form select:disabled').length;
    c()['guardar']();

    const p = backend.expectOne('/api/unidades-medida/5');
    expect(p.request.method).toBe('PUT');
    expect(p.request.body).toEqual({ nombre: 'Kilogramo' });
    expect(bloqueados).toBe(3);
    p.flush({ id: 5, nombre: 'Kilogramo' });
    backend.expectOne(r => r.url === '/api/unidades-medida').flush([]);
  });

  it('desactivar pide confirmación y activa con PATCH', () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    crear('sedes', ['ADMIN_SISTEMA'], [{ id: 1, nombre: 'Norte', activo: true }]);
    c()['alternar'](c()['filas']()[0]);

    const p = backend.expectOne('/api/sedes/1/desactivar');
    expect(p.request.method).toBe('PATCH');
    p.flush({ id: 1, activo: false });
    backend.expectOne(r => r.url === '/api/sedes').flush([]);
  });

  it('quien no administra el catálogo lo ve en modo consulta, sin botones', () => {
    crear('sedes', ['AUDITOR'], [{ id: 1, nombre: 'Norte', activo: true }]);

    expect(texto()).toContain('Solo consulta');
    expect(texto()).not.toContain('Desactivar');
    expect(texto()).not.toContain('Nueva sede');
  });

  it('un catálogo que no existe lo dice y no pide nada', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Catalogo);
    fixture.componentRef.setInput('catalogo', 'inventado');
    fixture.detectChanges();

    expect(texto()).toContain('Ese catálogo no existe');
  });
});

describe('Índice de configuración', () => {
  it('muestra solo lo del módulo activo y marca qué puede cambiar cada rol', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    const sesion = TestBed.inject(SesionService);
    sesion.usuario.set({ id: 3, nombre: 'Compras', correo: 'c@x.co', roles: ['ADMIN_COMPRAS'] });
    sesion.modulos.set({ modulos: ['COMPRAS', 'INVENTARIO'], estado: 'ACTIVA' });
    const f = TestBed.createComponent(Configuracion);
    f.detectChanges();
    const t = (f.nativeElement as HTMLElement).textContent ?? '';

    expect(t).toContain('Políticas de aprobación');
    expect(t).toContain('Productos');
    expect(t).not.toContain('Pesos de evaluación');          // módulo RFQ_COTIZACIONES apagado
    expect(t).not.toContain('Proveedores');                  // módulo PROVEEDORES apagado
    expect(t).toContain('Puedes cambiarlo');
    expect(t).toContain('Solo consulta');                    // sedes las administra ADMIN_SISTEMA
  });
});
