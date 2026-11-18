import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Proveedor } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Proveedores } from './proveedores';

const proveedor = (extra: Partial<Proveedor> = {}): Proveedor => ({
  id: 9, razonSocial: 'Eléctricos del Norte', nit: '900123', correoContacto: 'ventas@norte.co', telefono: '3001234567', estado: 'HABILITADO', scoreActual: 87.5,
  categorias: [{ id: 2, nombre: 'Eléctrico', activo: true }], creadoEn: '2026-10-01T10:00:00', ...extra,
});
const paginaEval = { contenido: [{ id: 1, ordenCompraId: 5, puntualidad: 90, cumplimientoCantidad: 100, calidad: 80, precio: 85, score: 88, creadoEn: '2026-11-10T10:00:00' }], pagina: 0, tamano: 10, totalElementos: 1, totalPaginas: 1 };

describe('Proveedores', () => {
  let fixture: ComponentFixture<Proveedores>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const montar = (roles: string[], p: Proveedor = proveedor()) => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Compras', correo: 'c@x.co', roles });
    fixture = TestBed.createComponent(Proveedores);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/categorias').flush([{ id: 2, nombre: 'Eléctrico', activo: true }, { id: 3, nombre: 'Ferretería', activo: true }]);
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [p], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 });
    fixture.detectChanges();
    c()['abrir'](p);
    backend.expectOne('/api/proveedores/9').flush(p);
    backend.expectOne(r => r.url === '/api/proveedores/9/evaluaciones').flush(paginaEval);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra la ficha con su score y sus calificaciones', () => {
    montar(['ADMIN_COMPRAS']);

    expect(texto()).toContain('Eléctricos del Norte');
    expect(texto()).toContain('Calificaciones');
    expect(texto()).toContain('Suspender');
  });

  it('suspender exige un motivo y lo manda; un suspendido se puede volver a habilitar', () => {
    montar(['ADMIN_COMPRAS']);
    c()['motivoPara'].set('suspender');
    c()['motivo'].set('no');
    c()['confirmarMotivo']();
    backend.expectNone('/api/proveedores/9/suspender');

    c()['motivo'].set('Incumplió dos entregas');
    c()['confirmarMotivo']();
    const p = backend.expectOne('/api/proveedores/9/suspender');
    expect(p.request.body).toEqual({ motivo: 'Incumplió dos entregas' });
    p.flush(proveedor({ estado: 'SUSPENDIDO' }));
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 });
    fixture.detectChanges();

    expect(texto()).toContain('Volver a habilitar');
  });

  it('editar manda contacto y categorías; exige al menos una categoría', () => {
    montar(['ADMIN_COMPRAS']);
    c()['editar']();
    c()['telefono'].set('3109998888');
    c()['alternarCategoria'](3);
    c()['guardar']();

    const p = backend.expectOne('/api/proveedores/9');
    expect(p.request.method).toBe('PUT');
    expect(p.request.body).toEqual({ correoContacto: 'ventas@norte.co', telefono: '3109998888', direccion: undefined, categoriaIds: [2, 3] });
    p.flush(proveedor({ telefono: '3109998888' }));
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [], pagina: 0, tamano: 100, totalElementos: 0, totalPaginas: 0 });

    c()['editar']();
    c()['categoriaIds'].set([]);
    c()['guardar']();
    backend.expectNone('/api/proveedores/9');
  });

  it('el comprador consulta la ficha pero no ve los botones de cambio', () => {
    montar(['COMPRADOR']);

    expect(texto()).toContain('Calificaciones');
    expect(texto()).not.toContain('Suspender');
    expect(texto()).not.toContain('Editar datos');
  });
});
