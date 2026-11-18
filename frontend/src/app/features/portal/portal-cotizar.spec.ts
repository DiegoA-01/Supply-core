import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PortalCotizar } from './portal-cotizar';

const rfq = { id: 2, consecutivo: 'RFQ-000002', estado: 'ABIERTA', fechaLimite: '2026-11-23T17:00:00',
  lineas: [{ id: 11, productoNombre: 'Breaker 20 A', productoCodigo: 'BRK-20', cantidad: 2, unidadCodigo: 'UND' }] };
const borrador = (extra: Record<string, unknown> = {}) => ({ id: 5, rfqId: 2, estado: 'BORRADOR', origen: 'ARCHIVO', lineas: [], ...extra });

describe('Portal: cotizar con archivo', () => {
  let fixture: ComponentFixture<PortalCotizar>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PortalCotizar);
    fixture.componentRef.setInput('rfqId', '2');
    fixture.detectChanges();
    backend.expectOne('/api/portal/rfqs/2').flush(rfq);
    backend.expectOne('/api/portal/rfqs/2/cotizacion').flush({ mensaje: 'no' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
  });

  afterEach(() => backend.verify());

  it('al subir el archivo queda a la vista que se adjuntó y con qué nombre', () => {
    const archivo = new File(['x'], 'cotizacion-electro-sur.png', { type: 'image/png' });
    c()['subirArchivo']({ target: { files: [archivo], value: 'C:\\fakepath\\cotizacion-electro-sur.png' } } as unknown as Event);
    const p = backend.expectOne('/api/portal/rfqs/2/cotizacion/archivo');
    expect(p.request.body instanceof FormData).toBe(true);
    p.flush(borrador({ documentoOriginalId: 40 }));
    fixture.detectChanges();

    expect(texto()).toContain('Archivo adjunto');
    expect(texto()).toContain('cotizacion-electro-sur.png');
    expect(texto()).toContain('Cambiar archivo');
  });

  it('con el archivo adjunto se puede enviar sin escribir precios', () => {
    c()['cotizacion'].set(borrador({ documentoOriginalId: 40 }));
    c()['guardar'](true);

    const p = backend.expectOne('/api/portal/rfqs/2/cotizacion/enviar');
    expect(p.request.method).toBe('POST');
    p.flush(borrador({ documentoOriginalId: 40, estado: 'ENVIADA', enviadoEn: '2026-11-18T16:00:00' }));
    fixture.detectChanges();

    expect(texto()).toContain('Cotización enviada');
    expect(texto()).toContain('archivo adjunto');
  });

  it('sin archivo ni precios no sale nada al servidor', () => {
    c()['guardar'](true);
    backend.expectNone(r => r.url.startsWith('/api/portal/rfqs/2/cotizacion'));
  });
});
