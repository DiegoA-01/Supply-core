import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { PortalCatalogoItem } from '../../core/api/modelos';
import { PortalCatalogo } from './portal-catalogo';

const articulo: PortalCatalogoItem = {
  codigoProveedor: 'C-12', descripcion: 'Cable THHN 12 rollo', marca: 'Centelsa', unidad: 'rollo', precio: 95000, incluyeIva: true, activo: true,
};

describe('Portal · Mi catálogo', () => {
  let fixture: ComponentFixture<PortalCatalogo>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (items: PortalCatalogoItem[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PortalCatalogo);
    fixture.detectChanges();
    backend.expectOne('/api/portal/catalogo').flush(items);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('lista el catálogo propio', () => {
    crear([articulo]);
    expect(texto()).toContain('Cable THHN 12 rollo');
    expect(texto()).toContain('Publicado');
  });

  it('sube el archivo y muestra los errores por fila sin guardar nada', () => {
    crear([]);
    c()['archivo'].set(new File(['x'], 'catalogo.xlsx'));
    c()['subir']();
    const req = backend.expectOne(r => r.url === '/api/portal/catalogo' && r.method === 'POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({ aplicado: false, nuevos: 0, actualizados: 0, retirados: 0, errores: [{ fila: 3, mensaje: 'Precio inválido' }] });
    fixture.detectChanges();
    expect(texto()).toContain('No se guardó nada');
    expect(texto()).toContain('Fila 3: Precio inválido');
  });

  it('al aplicarse recarga la lista', () => {
    crear([]);
    c()['archivo'].set(new File(['x'], 'catalogo.xlsx'));
    c()['subir']();
    backend.expectOne(r => r.url === '/api/portal/catalogo' && r.method === 'POST')
      .flush({ aplicado: true, nuevos: 1, actualizados: 0, retirados: 0, errores: [] });
    backend.expectOne(r => r.url === '/api/portal/catalogo' && r.method === 'GET').flush([articulo]);
    fixture.detectChanges();
    expect(texto()).toContain('1 nuevos');
    expect(texto()).toContain('Cable THHN 12 rollo');
  });
});
