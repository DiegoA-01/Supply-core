import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Orden, OfertaContrato } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { OrdenDetalle } from './orden-detalle';
import { OrdenDirecta } from './orden-directa';

const orden = (extra: Partial<Orden> = {}): Orden => ({
  id: 9, consecutivo: 'OC-000009', version: 1, estado: 'EMITIDA', proveedorRazonSocial: 'Eléctricos del Norte', bodegaDestinoId: 1, bodegaDestinoNombre: 'Central',
  lugarEntrega: 'Central', fechaEntregaEsperada: '2026-11-25', total: 1000000, creadoEn: '2026-11-18T09:00:00',
  lineas: [{ id: 41, productoNombre: 'Cable', unidad: 'm', cantidad: 100 }, { id: 42, productoNombre: 'Breaker', unidad: 'und', cantidad: 10 }], ...extra,
});

describe('Orden: cancelar y nueva versión', () => {
  let fixture: ComponentFixture<OrdenDetalle>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[], o: Orden = orden()) => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Comprador', correo: 'c@x.co', roles });
    fixture = TestBed.createComponent(OrdenDetalle);
    fixture.componentRef.setInput('id', '9');
    fixture.detectChanges();
    backend.expectOne('/api/ordenes/9').flush(o);
    backend.expectOne('/api/ordenes/9/facturas').flush([]);
    backend.expectOne('/api/ordenes/9/pagos').flush({ pagos: [] });
    backend.expectOne('/api/ordenes/9/recepciones').flush({ lineas: [], recepciones: [] });
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el comprador ve los botones mientras la orden no se despacha', () => {
    crear(['COMPRADOR']);

    expect(texto()).toContain('Nueva versión');
    expect(texto()).toContain('Cancelar orden');
  });

  it('no los ve si la orden ya se despachó ni si es otro rol', () => {
    crear(['COMPRADOR'], orden({ estado: 'DESPACHADA' }));
    expect(texto()).not.toContain('Nueva versión');
  });

  it('otro rol no ve los botones de cambio', () => {
    crear(['ALMACENISTA']);
    expect(texto()).not.toContain('Cancelar orden');
  });

  it('cancelar exige un motivo y lo manda como observación', () => {
    crear(['COMPRADOR']);
    c()['abrirCambio']('cancelar');
    c()['motivoCambio'].set('no');
    c()['cancelarOrden']();
    backend.expectNone('/api/ordenes/9/cancelar');

    c()['motivoCambio'].set('El proveedor no cumple la fecha');
    c()['cancelarOrden']();
    const p = backend.expectOne('/api/ordenes/9/cancelar');
    expect(p.request.body).toEqual({ observacion: 'El proveedor no cumple la fecha' });
    p.flush(orden({ estado: 'CANCELADA' }));
    backend.expectOne('/api/ordenes/9').flush(orden({ estado: 'CANCELADA' }));
    backend.expectOne('/api/ordenes/9/facturas').flush([]);
    backend.expectOne('/api/ordenes/9/pagos').flush({ pagos: [] });
    backend.expectOne('/api/ordenes/9/recepciones').flush({ lineas: [], recepciones: [] });
  });

  it('la versión nueva manda solo lo que cambió y no sale sin cambios ni sin motivo', () => {
    crear(['COMPRADOR']);
    c()['abrirCambio']('version');
    backend.expectOne(r => r.url === '/api/bodegas').flush([{ id: 1, nombre: 'Central', activo: true }]);

    c()['motivoCambio'].set('Cliente pidió menos');
    c()['crearVersion']();                         // sin cambios
    backend.expectNone('/api/ordenes/9/version');

    c()['cambiarCantidad'](41, 60);
    c()['versionFecha'].set('2026-12-01');
    c()['motivoCambio'].set('no');
    c()['crearVersion']();                         // motivo corto
    backend.expectNone('/api/ordenes/9/version');

    c()['motivoCambio'].set('Cliente pidió menos');
    c()['crearVersion']();
    const p = backend.expectOne('/api/ordenes/9/version');
    expect(p.request.body).toEqual({ motivo: 'Cliente pidió menos', bodegaDestinoId: undefined, lugarEntrega: undefined,
      fechaEntregaEsperada: '2026-12-01', cantidades: [{ lineaId: 41, cantidad: 60 }] });
    p.flush(orden({ id: 10, version: 2 }));
  });
});

describe('Orden directa por contrato', () => {
  let fixture: ComponentFixture<OrdenDirecta>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const oferta = (extra: Partial<OfertaContrato>): OfertaContrato => ({
    contratoId: 1, numero: 'CT-001', proveedorRazonSocial: 'Eléctricos del Norte', vigenteHasta: '2027-01-31', cubreTodo: true, productosSinCubrir: [],
    dentroDelTope: true, dentroDeLoAprobado: true, puedeEmitirse: true, subtotal: 900000, impuestos: 171000, total: 1071000, montoAprobado: 1200000, lineas: [], ...extra,
  });

  const crear = () => {
    TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(OrdenDirecta);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/solicitudes').flush({ contenido: [{ id: 7, consecutivo: 'SC-000007', fechaRequerida: '2026-11-30' }], pagina: 0, tamano: 50, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne(r => r.url === '/api/bodegas').flush([{ id: 1, nombre: 'Central', activo: true }]);
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('busca los contratos de la solicitud y deja elegido el primero que se puede emitir', () => {
    crear();
    c()['elegirSolicitud'](7);
    const p = backend.expectOne(r => r.url === '/api/contratos/ofertas');
    expect(p.request.params.get('solicitudId')).toBe('7');
    p.flush([oferta({ contratoId: 1, puedeEmitirse: false, cubreTodo: false, productosSinCubrir: ['Breaker'] }), oferta({ contratoId: 2, numero: 'CT-002' })]);
    fixture.detectChanges();

    expect(c()['elegido']()).toBe(2);
    expect(texto()).toContain('No cubre: Breaker');
    expect(texto()).toContain('Se puede emitir');
  });

  it('sin contratos que la cubran explica que se cotiza normal', () => {
    crear();
    c()['elegirSolicitud'](7);
    backend.expectOne(r => r.url === '/api/contratos/ofertas').flush([]);
    fixture.detectChanges();

    expect(texto()).toContain('Se cotiza normalmente');
  });

  it('emite con el contrato, la bodega, el lugar y la fecha; no sale si falta algo', () => {
    crear();
    c()['elegirSolicitud'](7);
    backend.expectOne(r => r.url === '/api/contratos/ofertas').flush([oferta({ contratoId: 2 })]);
    c()['emitir']();
    backend.expectNone('/api/ordenes/directa');

    c()['elegirBodega'](1);
    c()['fecha'].set('2026-11-25');
    c()['emitir']();
    const p = backend.expectOne('/api/ordenes/directa');
    expect(p.request.body).toEqual({ solicitudId: 7, contratoId: 2, bodegaDestinoId: 1, lugarEntrega: 'Central', fechaEntregaEsperada: '2026-11-25' });
    p.flush({ id: 11, consecutivo: 'OC-000011' });
  });
});
