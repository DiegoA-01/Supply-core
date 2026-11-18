import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Pagina, Pago } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Pagos } from './pagos';

const pago = (extra: Partial<Pago>): Pago => ({
  id: 1, ordenId: 9, ordenConsecutivo: 'OC-000009', ordenVersion: 1, proveedorRazonSocial: 'Eléctricos del Norte', metodo: 'TRANSFERENCIA',
  tipo: 'PAGO_FINAL', valor: 1500000, fechaPago: '2026-11-10', referencia: 'TR-88231', estado: 'REGISTRADO', evidenciaPendiente: true, ...extra,
});
const pagina: Pagina<Pago> = {
  contenido: [pago({ id: 1 }), pago({ id: 2, evidenciaPendiente: false, comprobanteDocumentoId: 40 }),
    pago({ id: 3, estado: 'ANULADO', evidenciaPendiente: false, motivoAnulacion: 'Se registró dos veces' })],
  pagina: 0, tamano: 20, totalElementos: 3, totalPaginas: 1,
};

describe('Pagos', () => {
  let fixture: ComponentFixture<Pagos>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Tesorería', correo: 't@x.co', roles });
    fixture = TestBed.createComponent(Pagos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/proveedores').flush({ contenido: [{ id: 4, razonSocial: 'Eléctricos del Norte' }], pagina: 0, tamano: 100, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne(r => r.url === '/api/pagos').flush(pagina);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('muestra cada pago con su estado y qué comprobante tiene', () => {
    crear(['ADMINISTRATIVO']);

    expect(texto()).toContain('OC-000009');
    expect(texto()).toContain('Pendiente');
    expect(texto()).toContain('Adjunto');
    expect(texto()).toContain('Se registró dos veces');
  });

  it('tesorería puede adjuntar el comprobante y anular; los pagos anulados no ofrecen acciones', () => {
    crear(['ADMINISTRATIVO']);

    expect(texto()).toContain('Adjuntar comprobante');
    expect(texto()).toContain('Ver comprobante');
    expect(fixture.nativeElement.querySelectorAll('button.peligro').length).toBe(2);   // solo los 2 registrados
  });

  it('auditoría consulta pero no puede adjuntar ni anular', () => {
    crear(['AUDITOR']);

    expect(texto()).toContain('Ver comprobante');
    expect(texto()).not.toContain('Adjuntar comprobante');
    expect(fixture.nativeElement.querySelectorAll('button.peligro').length).toBe(0);
  });

  it('los filtros se mandan al servidor y "sin comprobante" solo se manda si está marcado', () => {
    crear(['ADMINISTRATIVO']);
    c()['sinComprobante'].set(true);
    c()['proveedorId'].set(4);
    c()['desde'].set('2026-11-01');
    c()['buscar'](0);

    const p = backend.expectOne(r => r.url === '/api/pagos');
    expect(p.request.params.get('evidenciaPendiente')).toBe('true');
    expect(p.request.params.get('proveedorId')).toBe('4');
    expect(p.request.params.get('desde')).toBe('2026-11-01');
    p.flush(pagina);

    c()['limpiar']();
    const q = backend.expectOne(r => r.url === '/api/pagos');
    expect(q.request.params.has('evidenciaPendiente')).toBe(false);
    q.flush(pagina);
  });

  it('anular un pago pide motivo y manda el motivo; luego recarga la lista', () => {
    crear(['ADMINISTRATIVO']);
    const boton = Array.from(fixture.nativeElement.querySelectorAll('button.peligro') as NodeListOf<HTMLButtonElement>)[0]!;
    boton.click();
    fixture.detectChanges();

    const caja = fixture.nativeElement.querySelector('input[id^="motivo-"]') as HTMLInputElement;
    caja.value = 'no';
    caja.dispatchEvent(new Event('input'));
    const enviar = Array.from(fixture.nativeElement.querySelectorAll('button.peligro') as NodeListOf<HTMLButtonElement>).find(b => b.textContent?.includes('Anular pago'))!;
    enviar.click();
    backend.expectNone(r => r.url.endsWith('/anular'));

    caja.value = 'Se registró dos veces';
    caja.dispatchEvent(new Event('input'));
    enviar.click();
    const p = backend.expectOne('/api/pagos/1/anular');
    expect(p.request.body).toEqual({ motivo: 'Se registró dos veces' });
    p.flush(pago({ id: 1, estado: 'ANULADO' }));
    backend.expectOne(r => r.url === '/api/pagos').flush(pagina);
  });
});
