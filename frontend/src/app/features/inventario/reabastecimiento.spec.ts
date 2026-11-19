import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { ItemReabastecimiento, Reabastecimiento } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PlanReabastecimiento } from './reabastecimiento';

const item = (extra: Partial<ItemReabastecimiento>): ItemReabastecimiento => ({
  productoId: 1, codigo: 'X', nombre: 'X', categoria: 'Ferretería', unidad: 'UND', stock: 10, stockMinimo: 0, porRecibir: 0,
  enSolicitudes: 0, consumoDiario: 1, diasCobertura: 10, fechaAgotamiento: '2026-11-28', plazoEntregaDias: 6, plazoPropio: false,
  diasSeguridad: 2, entregasHistoricas: 0, llegadaSiPidoHoy: '2026-11-26', puntoPedido: 12, pedirAntesDe: '2026-11-18',
  cantidadSugerida: 32, estado: 'PEDIR_YA', motivo: 'Hay que pedir hoy.', sobrante: 0, cantidadTraslado: 0, cantidadComprar: 32, ...extra,
});

const datos: Reabastecimiento = {
  hoy: '2026-11-18', diasConsumo: 90, plazoEntregaDefecto: 6, seguridadDefecto: 2, coberturaDefecto: 30,
  urgentes: 1, pedirYa: 1, pronto: 0, ok: 1, sinConsumo: 0,
  items: [
    item({ productoId: 1, codigo: 'TOR-01', nombre: 'Tornillo', estado: 'URGENTE', stock: 5, cantidadSugerida: 37, cantidadComprar: 37 }),
    item({ productoId: 2, codigo: 'CAB-12', nombre: 'Cable', plazoHistorico: 9, entregasHistoricas: 3 }),
    item({ productoId: 3, codigo: 'MAR-01', nombre: 'Martillo', estado: 'OK', stock: 100, cantidadSugerida: 0, cantidadComprar: 0, pedirAntesDe: '2026-12-30' }),
  ],
};

describe('Plan de reabastecimiento', () => {
  let fixture: ComponentFixture<PlanReabastecimiento>;
  let backend: HttpTestingController;

  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    TestBed.inject(SesionService).usuario.set({ id: 3, nombre: 'Ana', correo: 'a@x.co', roles });
    fixture = TestBed.createComponent(PlanReabastecimiento);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/reabastecimiento/sedes').flush([]);   // comparación (una sola sede)
    backend.expectOne(r => r.url === '/api/reabastecimiento').flush(datos);
    fixture.detectChanges();
    backend.expectOne(r => r.url === '/api/sedes').flush([]);   // selector de sede
  };

  afterEach(() => backend.verify());

  it('muestra los conteos del backend y por defecto solo lo que hay que pedir', () => {
    crear(['COMPRADOR']);
    expect(texto()).toContain('Urgente');
    expect(texto()).toContain('6 días hábiles');
    expect(c()['items']().map((i: ItemReabastecimiento) => i.nombre)).toEqual(['Tornillo', 'Cable']);
    c()['filtro'].set(null);
    expect(c()['items']().length).toBe(3);
  });

  it('el solicitante deja elegidos los urgentes y los de pedir hoy y crea el borrador con ellos', () => {
    crear(['SOLICITANTE']);
    expect([...c()['elegidos']()]).toEqual([1, 2]);
    expect(c()['fechaRequerida']()).toBe('2026-11-26');
    c()['crearSolicitud']();
    const req = backend.expectOne('/api/reabastecimiento/borrador');
    expect(req.request.body).toEqual({ fechaRequerida: '2026-11-26', productoIds: [1, 2], sedeId: null });
    req.flush({ id: 9, consecutivo: 'SOL-000009' });
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/app/solicitudes', 9, 'editar']);
  });

  it('marca cuando las entregas reales tardan distinto del plazo configurado', () => {
    crear(['ADMIN_COMPRAS']);
    expect(c()['historicoDistinto'](datos.items![1])).toBe(true);
    expect(c()['historicoDistinto'](datos.items![0])).toBe(false);
    expect(c()['puedePedir']()).toBe(false);
    expect(c()['puedeAjustar']()).toBe(true);
  });

  it('en la vista de una sede muestra el traslado sugerido y pide solo lo que falta comprar', () => {
    crear(['SOLICITANTE']);
    c()['cambiarSede'](4);
    backend.expectOne(r => r.url === '/api/reabastecimiento' && r.params.get('sedeId') === '4').flush({ ...datos, items: [
      item({ productoId: 2, codigo: 'CAB-12', nombre: 'Cable', cantidadSugerida: 32, cantidadTraslado: 20,
        trasladoDesdeSedeId: 5, trasladoDesdeSede: 'Sede Sur', cantidadComprar: 12 }),
    ] });
    fixture.detectChanges();
    expect(texto()).toContain('Trasladar 20');
    expect(texto()).toContain('desde Sede Sur');
    expect(texto()).toContain('y comprar 12');
    expect(c()['comparacion']()).toEqual([]);
  });

  it('con varias sedes, en toda la empresa muestra la comparación', () => {
    crear(['COMPRADOR']);
    c()['comparacion'].set([{ sedeId: 1, sede: 'Sede Centro', productos: 3, urgentes: 1, pedirYa: 0, pronto: 0, ok: 2, sinConsumo: 0, traslados: 1 },
      { sedeId: 2, sede: 'Sede Norte', productos: 3, urgentes: 0, pedirYa: 0, pronto: 0, ok: 3, sinConsumo: 0, traslados: 0 }]);
    fixture.detectChanges();
    expect(texto()).toContain('Comparar sedes');
    expect(texto()).toContain('Sede Norte');
  });

  it('guarda el plazo propio de un producto y recalcula el plan', () => {
    crear(['ADMIN_COMPRAS']);
    c()['editarPlazo'](datos.items![1]);
    c()['cambiarEdicion']('plazo', '7');
    c()['cambiarEdicion']('seguridad', '');
    c()['guardarPlazo']();
    const req = backend.expectOne('/api/productos/2/plazo');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ plazoEntregaDias: 7, diasSeguridad: null });
    req.flush({});
    backend.expectOne(r => r.url === '/api/reabastecimiento/sedes').flush([]);
    backend.expectOne(r => r.url === '/api/reabastecimiento').flush(datos);
  });
});
