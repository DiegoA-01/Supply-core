import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Despacho, DespachoResumen, Pagina } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Despachos } from './despachos';

const resumen: DespachoResumen = { id: 7, consecutivo: 'DE-000007', estado: 'BORRADOR', bodegaId: 1, cliente: 'Obra Norte', creadoEn: '2026-11-18T09:00:00' };
const pagina: Pagina<DespachoResumen> = { contenido: [resumen], pagina: 0, tamano: 20, totalElementos: 1, totalPaginas: 1 };
const detalle = (extra: Partial<Despacho> = {}): Despacho => ({
  id: 7, consecutivo: 'DE-000007', estado: 'BORRADOR', bodegaId: 1, bodegaNombre: 'Central', cliente: 'Obra Norte', creadoEn: '2026-11-18T09:00:00',
  lineas: [{ id: 1, productoId: 10, productoCodigo: 'CEM-50', productoNombre: 'Cemento', unidadMedidaId: 5, unidadCodigo: 'und', cantidad: 3, cantidadBase: 3, unidadProductoCodigo: 'und' }],
  productosBajoMinimo: [], ...extra,
});

describe('Despachos', () => {
  let fixture: ComponentFixture<Despachos>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Bodega', correo: 'b@x.co', roles });
    fixture = TestBed.createComponent(Despachos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/bodegas').flush([{ id: 1, nombre: 'Central', activo: true }]);
    backend.expectOne(r => r.url === '/api/unidades-medida').flush([{ id: 5, codigo: 'und', activo: true }]);
    backend.expectOne(r => r.url === '/api/despachos').flush(pagina);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  describe('almacenista', () => {
    beforeEach(() => crear(['ALMACENISTA']));

    it('lista los despachos y abre el detalle con lo que descuenta del stock', () => {
      expect(texto()).toContain('DE-000007');
      c()['abrir'](resumen);
      backend.expectOne('/api/despachos/7').flush(detalle());
      fixture.detectChanges();

      expect(texto()).toContain('Descuenta del stock');
      expect(texto()).toContain('Confirmar y descontar stock');
    });

    it('arma un despacho nuevo y manda bodega, productos, unidad y cantidad', () => {
      c()['nuevo']();
      const linea = c()['lineas']()[0];
      linea.buscador.opciones.set([{ id: 10, codigo: 'CEM-50', nombre: 'Cemento', unidadMedida: { id: 5, codigo: 'und' } }]);
      linea.buscador.texto.set('CEM-50 · Cemento');
      c()['cambiarLinea'](0, 'cantidad', 3);
      c()['cliente'].set('Obra Norte');
      c()['guardar']();

      const p = backend.expectOne('/api/despachos');
      expect(p.request.method).toBe('POST');
      expect(p.request.body).toEqual({ bodegaId: 1, cliente: 'Obra Norte', referenciaExterna: undefined, observaciones: undefined,
        lineas: [{ productoId: 10, unidadMedidaId: 5, cantidad: 3 }] });
      p.flush(detalle());
      backend.expectOne(r => r.url === '/api/despachos').flush(pagina);
    });

    it('no envía un despacho con productos repetidos, sin cantidad o sin producto elegido', () => {
      c()['nuevo']();
      c()['guardar']();
      backend.expectNone('/api/despachos');

      const opciones = [{ id: 10, codigo: 'CEM-50', nombre: 'Cemento', unidadMedida: { id: 5, codigo: 'und' } }];
      c()['agregarLinea']();
      for (const l of c()['lineas']()) {
        l.buscador.opciones.set(opciones);
        l.buscador.texto.set('CEM-50 · Cemento');
      }
      c()['cambiarLinea'](0, 'cantidad', 1);
      c()['cambiarLinea'](1, 'cantidad', 2);
      c()['guardar']();

      backend.expectNone('/api/despachos');
    });

    it('confirma el despacho y muestra los productos que quedaron bajo su mínimo', () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      c()['detalle'].set(detalle());
      c()['confirmar']();

      const p = backend.expectOne('/api/despachos/7/confirmar');
      p.flush(detalle({ estado: 'CONFIRMADO', confirmadoEn: '2026-11-18T10:00:00', productosBajoMinimo: ['CEM-50 · Cemento'] }));
      backend.expectOne(r => r.url === '/api/despachos').flush(pagina);
      fixture.detectChanges();

      expect(texto()).toContain('Quedaron bajo su mínimo');
      expect(texto()).toContain('Un despacho confirmado ya no se edita');
    });

    it('anular exige un motivo de 5 caracteres', () => {
      c()['detalle'].set(detalle({ estado: 'CONFIRMADO' }));
      c()['motivo'].set('no');
      c()['anular']();
      backend.expectNone('/api/despachos/7/anular');

      c()['motivo'].set('El cliente devolvió todo');
      c()['anular']();
      const p = backend.expectOne('/api/despachos/7/anular');
      expect(p.request.body).toEqual({ motivo: 'El cliente devolvió todo' });
      p.flush(detalle({ estado: 'ANULADO', confirmadoEn: '2026-11-18T10:00:00', motivoAnulacion: 'El cliente devolvió todo' }));
      backend.expectOne(r => r.url === '/api/despachos').flush(pagina);
    });
  });

  describe('quien solo consulta', () => {
    beforeEach(() => crear(['AUDITOR']));

    it('no ve botones para armar, confirmar ni anular', () => {
      c()['abrir'](resumen);
      backend.expectOne('/api/despachos/7').flush(detalle());
      fixture.detectChanges();

      expect(texto()).not.toContain('Nuevo despacho');
      expect(texto()).not.toContain('Confirmar y descontar stock');
      expect(texto()).not.toContain('Anular');
    });
  });

  describe('jefe de compras', () => {
    beforeEach(() => crear(['ADMIN_COMPRAS']));

    it('puede anular pero no armar ni confirmar', () => {
      c()['abrir'](resumen);
      backend.expectOne('/api/despachos/7').flush(detalle({ estado: 'CONFIRMADO' }));
      fixture.detectChanges();

      expect(texto()).toContain('Anular');
      expect(texto()).not.toContain('Nuevo despacho');
      expect(texto()).not.toContain('Confirmar y descontar stock');
    });
  });
});
