import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { InformeCompras, InformeContenido } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { Informes } from './informes';

const contenido: InformeContenido = {
  desde: '2026-11-01', hasta: '2026-11-30', generadoEn: '2026-11-18T16:00:00',
  resumen: { solicitudes: 3, montoSolicitado: 1500000, solicitudesPorEstado: { APROBADA: 2, PENDIENTE_APROBACION: 1 }, rfqs: 1,
    cotizacionesRecibidas: 2, adjudicaciones: 1, adjudicacionesFueraDeRecomendacion: 1, montoAdjudicado: 83776 },
  solicitudes: [
    { consecutivo: 'SOL-000002', solicitante: 'Bodeguero', centroCosto: 'Obras', estado: 'EN_RFQ', montoEstimado: 420000, montoIncompleto: false, motivo: 'Se acaba el cable',
      lineas: [{ codigo: 'CAB-12', producto: 'Cable 12 AWG', cantidad: 150, unidad: 'UND', cantidadEnUnidadProducto: 150, unidadProducto: 'UND', costoReferencia: 2800, subtotal: 420000 }], montoCalculado: 420000, cuadra: true },
    { consecutivo: 'SOL-000001', solicitante: 'Solicitante', centroCosto: 'Obras', estado: 'PENDIENTE_APROBACION', montoEstimado: 1110000, montoIncompleto: false, motivo: 'Pintura',
      lineas: [{ codigo: 'PIN-20', producto: 'Pintura blanca 20 L', cantidad: 6, unidad: 'CAJA24', cantidadEnUnidadProducto: 144, unidadProducto: 'UND', costoReferencia: 7800, subtotal: 1123200 }], montoCalculado: 1123200, cuadra: false },
  ],
  rfqs: [{ consecutivo: 'RFQ-000001', solicitud: 'SOL-000002', estado: 'ABIERTA', invitados: 2, cotizaciones: 1 }],
  cotizaciones: [{ rfq: 'RFQ-000001', proveedor: 'Electro Sur Ltda.', estado: 'ENVIADA', total: 83776, plazoEntregaDias: 5 }],
  adjudicaciones: [{ rfq: 'RFQ-000001', proveedor: 'Electro Sur Ltda.', montoTotal: 83776, sigueRecomendacion: false, justificacion: 'Entrega más rápida', estado: 'APROBADA' }],
  gasto: { ordenes: 1, total: 83776, pagado: 0, porProveedor: [], porCategoria: [], porMes: [] } as never,
  recortado: false,
};
const pedido: InformeCompras = { id: 4, estado: 'PEDIDO', desde: '2026-11-01', hasta: '2026-11-30', mensajePedido: '¿Cómo vamos con eléctricos?', pedidoPor: 'Dueño Ferretería', pedidoEn: '2026-11-18T09:00:00' };
const enviado: InformeCompras = { id: 3, estado: 'ENVIADO', desde: '2026-10-01', hasta: '2026-10-31', enviadoPor: 'Jefe de compras', enviadoEn: '2026-11-02T10:00:00', leidoEn: null };
const pagina = (l: InformeCompras[]) => ({ contenido: l, pagina: 0, tamano: 20, totalElementos: l.length, totalPaginas: 1 });

describe('Informe de compras', () => {
  let fixture: ComponentFixture<Informes>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Informes);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/informes-compras').flush(pagina([pedido, enviado]));
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('el dueño pide un informe con su periodo y su mensaje', () => {
    crear(['ADMIN_SISTEMA']);
    expect(texto()).toContain('Pedir un informe');
    expect(texto()).not.toContain('Tu análisis');
    c()['pedirDesde'].set('2026-11-01');
    c()['pedirHasta'].set('2026-11-30');
    c()['mensaje'].set('  Gasto en eléctricos  ');
    c()['pedir']();

    const p = backend.expectOne('/api/informes-compras/pedir');
    expect(p.request.body).toEqual({ desde: '2026-11-01', hasta: '2026-11-30', mensaje: 'Gasto en eléctricos' });
    p.flush(pedido);
    backend.expectOne(r => r.url === '/api/informes-compras').flush(pagina([pedido]));
  });

  it('al abrir uno enviado el dueño lo marca como leído y ve el análisis y los datos', () => {
    crear(['ADMIN_SISTEMA']);
    c()['abrir'](enviado);
    backend.expectOne('/api/informes-compras/3/leido').flush({ ...enviado, leidoEn: '2026-11-18T10:00:00', analisis: 'Bajamos el gasto en cable.', contenido });
    backend.expectOne(r => r.url === '/api/informes-compras').flush(pagina([pedido, { ...enviado, leidoEn: '2026-11-18T10:00:00' }]));
    fixture.detectChanges();

    expect(texto()).toContain('Bajamos el gasto en cable.');
    expect(texto()).toContain('SOL-000002');
    expect(texto()).toContain('Se apartó');
    expect(texto()).toContain('Entrega más rápida');
  });

  it('cada solicitud muestra qué productos pidió, cuántos, la cuenta y si el monto cuadra', () => {
    crear(['ADMIN_SISTEMA']);
    c()['abrir'](enviado);
    backend.expectOne('/api/informes-compras/3/leido').flush({ ...enviado, leidoEn: '2026-11-18T10:00:00', analisis: 'Ok.', contenido });
    backend.expectOne(r => r.url === '/api/informes-compras').flush(pagina([]));
    fixture.detectChanges();

    expect(texto()).toContain('Qué pidió y cuánto');
    expect(texto()).toContain('Cable 12 AWG');
    expect(texto()).toContain('150 UND');
    expect(texto()).toContain('144 UND');      // 6 cajas pasadas a la unidad del costo
    expect(texto()).toContain('Cuadra');
    expect(texto()).toContain('No cuadra');
    expect(fixture.nativeElement.querySelectorAll('.linea').length).toBe(2);
  });

  it('compras responde el pedido: toma su periodo, revisa los datos y envía con su análisis', () => {
    crear(['ADMIN_COMPRAS']);
    expect(texto()).not.toContain('Pedir un informe');
    c()['responder'](pedido);
    const vista = backend.expectOne(r => r.url === '/api/informes-compras/vista-previa');
    expect(vista.request.params.get('desde')).toBe('2026-11-01');
    vista.flush(contenido);
    fixture.detectChanges();
    expect(texto()).toContain('Responder el pedido del dueño');
    expect(texto()).toContain('Electro Sur Ltda.');

    c()['analisis'].set('Corto: falta cable, la pintura no rota.');
    c()['enviar']();
    const p = backend.expectOne(r => r.url === '/api/informes-compras' && r.method === 'POST');
    expect(p.request.body).toEqual({ desde: '2026-11-01', hasta: '2026-11-30', analisis: 'Corto: falta cable, la pintura no rota.', pedidoId: 4 });
    p.flush({ ...pedido, estado: 'ENVIADO', analisis: 'Corto: falta cable, la pintura no rota.', contenido });
    backend.expectOne(r => r.url === '/api/informes-compras' && r.method === 'GET').flush(pagina([]));
  });

  it('sin análisis no se envía', () => {
    crear(['ADMIN_COMPRAS']);
    c()['analisis'].set('corto');
    c()['enviar']();
    backend.expectNone(r => r.method === 'POST');
  });
});
