import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { CatalogoItem, Oportunidad } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { CatalogosProveedores } from './catalogos';

const item = (extra: Partial<CatalogoItem>): CatalogoItem => ({
  id: 7, proveedorId: 2, proveedor: 'Ferrecol', codigoProveedor: 'C-12', descripcion: 'Cable THHN 12 rollo',
  precio: 95000, incluyeIva: true, productoId: 4, producto: 'Cable 12 AWG', estadoEquivalencia: 'SUGERIDA',
  similitud: 0.82, actualizadoEn: '2026-11-18T10:00:00', ...extra,
});
const oportunidad: Oportunidad = {
  productoId: 4, codigo: 'CAB-12', nombre: 'Cable 12 AWG', unidad: 'rollo', proveedorId: 2, proveedor: 'Ferrecol',
  descripcionProveedor: 'Cable THHN 12 rollo', precioSinIva: 79832, costoActual: 90000, ahorroPct: 11.3,
  estadoReabastecimiento: 'PEDIR_YA', cantidadSugerida: 10, motivo: 'Más barato que tu último costo y hay que pedir hoy.',
};

describe('Catálogos de proveedores', () => {
  let fixture: ComponentFixture<CatalogosProveedores>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[], ops: Oportunidad[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Ana', correo: 'a@x.co', roles });
    fixture = TestBed.createComponent(CatalogosProveedores);
    fixture.detectChanges();
    backend.expectOne('/api/catalogos/oportunidades').flush(ops);
    fixture.detectChanges();
  };
  const pagina = (contenido: CatalogoItem[]) => {
    backend.expectOne(r => r.url === '/api/catalogos' && r.method === 'GET')
      .flush({ contenido, pagina: 0, tamano: 30, totalElementos: contenido.length, totalPaginas: 1 });
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra las oportunidades con el ahorro que calculó el backend', () => {
    crear(['COMPRADOR'], [oportunidad]);
    expect(texto()).toContain('Cable 12 AWG');
    expect(texto()).toContain('más barato');
    expect(texto()).toContain('Pedir hoy');
  });

  it('sin oportunidades explica de dónde salen', () => {
    crear(['COMPRADOR'], []);
    expect(texto()).toContain('No hay oportunidades por ahora');
  });

  it('el comprador confirma la equivalencia sugerida', () => {
    crear(['COMPRADOR'], []);
    c()['cambiar']('catalogo');
    pagina([item({})]);
    c()['confirmar'](item({}));
    const req = backend.expectOne(r => r.url === '/api/catalogos/7' && r.method === 'PATCH');
    expect(req.request.body).toEqual({ estado: 'CONFIRMADA', productoId: 4 });
    req.flush(item({ estadoEquivalencia: 'CONFIRMADA' }));
    backend.expectOne('/api/catalogos/oportunidades').flush([oportunidad]);
    fixture.detectChanges();
    expect(texto()).toContain('Confirmada');
  });

  it('el auditor ve el catálogo pero no decide', () => {
    crear(['AUDITOR'], []);
    c()['cambiar']('catalogo');
    pagina([item({})]);
    expect(c()['decide']()).toBe(false);
    const botones = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map(b => b.textContent?.trim());
    expect(botones).not.toContain('Confirmar');
  });
});
