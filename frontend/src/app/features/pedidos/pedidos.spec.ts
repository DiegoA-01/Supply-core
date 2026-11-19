import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { PedidoInterno } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PedidosInternos } from './pedidos';

const pedido = (extra: Partial<PedidoInterno>): PedidoInterno => ({
  id: 1, consecutivo: 'PED-000001', usuarioId: 3, solicitante: 'Bodega', descripcion: 'Guantes de carnaza',
  estado: 'PENDIENTE', creadoEn: '2026-11-19T08:00:00', ...extra,
});

describe('Pedidos internos', () => {
  let fixture: ComponentFixture<PedidosInternos>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Ana', correo: 'a@x.co', roles });
    fixture = TestBed.createComponent(PedidosInternos);
    fixture.detectChanges();
  };
  const lista = (mios: string, contenido: PedidoInterno[]) => {
    backend.expectOne(r => r.url === '/api/pedidos-internos' && r.params.get('mios') === mios)
      .flush({ contenido, pagina: 0, tamano: 20, totalElementos: contenido.length, totalPaginas: 1 });
    backend.match(r => r.url === '/api/sedes').forEach(r => r.flush([]));
    fixture.detectChanges();
  };

  afterEach(() => backend.verify());

  it('quien no es de compras ve solo sus pedidos y no puede responder', () => {
    crear(['ALMACENISTA']);
    lista('true', [pedido({})]);
    expect(texto()).toContain('Guantes de carnaza');
    expect(c()['responde']()).toBe(false);
    const botones = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map(b => b.textContent?.trim());
    expect(botones).not.toContain('Descartar');
  });

  it('envía un pedido con descripción libre cuando el producto no está en el catálogo', () => {
    crear(['ALMACENISTA']);
    lista('true', []);
    c()['descripcion'].set('Guantes de carnaza talla L');
    c()['cantidad'].set(6);
    c()['pedir']();
    const req = backend.expectOne(r => r.url === '/api/pedidos-internos' && r.method === 'POST');
    expect(req.request.body).toEqual({ productoId: null, descripcion: 'Guantes de carnaza talla L', cantidad: 6, sedeId: null, nota: null });
    req.flush(pedido({}));
    lista('true', [pedido({})]);
  });

  it('compras ve todos y descartar exige explicar', () => {
    crear(['COMPRADOR']);
    lista('false', [pedido({})]);
    expect(c()['responde']()).toBe(true);
    c()['responder'](pedido({}), 'DESCARTADO');
    c()['guardarRespuesta']({ id: 1, estado: 'DESCARTADO', texto: '  ' });
    backend.expectNone(r => r.method === 'PATCH');
    c()['guardarRespuesta']({ id: 1, estado: 'DESCARTADO', texto: 'Ya no se vende' });
    const req = backend.expectOne(r => r.url === '/api/pedidos-internos/1' && r.method === 'PATCH');
    expect(req.request.body).toEqual({ estado: 'DESCARTADO', respuesta: 'Ya no se vende' });
    req.flush(pedido({ estado: 'DESCARTADO' }));
    lista('false', [pedido({ estado: 'DESCARTADO', respuesta: 'Ya no se vende' })]);
  });
});
