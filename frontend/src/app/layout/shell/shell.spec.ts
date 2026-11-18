import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { Shell } from './shell';

const proveedor = (id: number, razonSocial: string) => ({ id, razonSocial, nit: '800123456-1', estado: 'PENDIENTE' });
const pagina = (contenido: unknown[]) => ({ contenido, pagina: 0, tamano: 100, totalElementos: contenido.length, totalPaginas: 1 });

/** Sesión mínima: solo lo que lee el marco. */
const sesionCon = (roles: string[]) => ({
  empresa: signal({ razonSocial: 'Ferretería Demo' }),
  modulos: signal({ plan: 'EMPRESARIAL', estado: 'ACTIVA', modulos: ['COMPRAS', 'INVENTARIO', 'PROVEEDORES'] }),
  usuario: signal({ nombre: 'Dueño Ferretería', correo: 'a@x.co', roles }),
  soloLectura: () => false,
  tieneRol: (r?: readonly string[]) => !r?.length || r.some(x => roles.includes(x)),
  tieneModulo: () => true,
  salir: () => undefined,
  salirDeTodo: () => undefined,
});

describe('Marco de la empresa: campana y menú', () => {
  let fixture: ComponentFixture<Shell>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const html = () => fixture.nativeElement as HTMLElement;

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: sesionCon(roles) }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Shell);
    fixture.detectChanges();
    backend.match(r => r.url === '/api/notificaciones/no-leidas').forEach(r => r.flush({ cantidad: 0 }));
    backend.match(r => r.url === '/api/solicitudes/pendientes-aprobacion').forEach(r => r.flush(pagina([])));
    backend.match(r => r.url === '/api/productos/alertas-stock').forEach(r => r.flush([]));
  };

  afterEach(() => backend.verify());

  describe('quien administra compras', () => {
    beforeEach(() => {
      crear(['ADMIN_SISTEMA', 'ADMIN_COMPRAS']);
      backend.expectOne(r => r.url === '/api/proveedores' && r.params.get('estado') === 'PENDIENTE')
        .flush(pagina([proveedor(7, 'Electro Sur'), proveedor(8, 'Cables del Norte')]));
      fixture.detectChanges();
    });

    it('la campana muestra cuántas decisiones esperan', () => {
      expect(c()['totalAvisos']()).toBe(2);
      expect(html().querySelector('.campana .insignia')?.textContent?.trim()).toBe('2');
    });

    it('aceptar habilita al proveedor y lo saca de la lista', () => {
      c()['aceptarProveedor'](7);
      const p = backend.expectOne('/api/proveedores/7/habilitar');
      expect(p.request.method).toBe('PATCH');
      p.flush(proveedor(7, 'Electro Sur'));

      expect(c()['pendientes']().map((x: any) => x.id)).toEqual([8]);
    });

    it('rechazar manda el motivo y lo saca de la lista', () => {
      c()['pedirMotivo'](8);
      c()['motivo'].set('No tiene certificado vigente');
      c()['rechazarProveedor'](8);
      const p = backend.expectOne('/api/proveedores/8/rechazar');
      expect(p.request.body).toEqual({ motivo: 'No tiene certificado vigente' });
      p.flush(proveedor(8, 'Cables del Norte'));

      expect(c()['pendientes']().map((x: any) => x.id)).toEqual([7]);
      expect(c()['rechazando']()).toBeNull();
    });

    it('si el servidor falla, el proveedor sigue en la lista y se avisa', () => {
      c()['aceptarProveedor'](7);
      backend.expectOne('/api/proveedores/7/habilitar').flush({ mensaje: 'x' }, { status: 409, statusText: 'Conflict' });

      expect(c()['pendientes']().length).toBe(2);
      expect(c()['errorDecision']()).toContain('No se pudo aceptar');
    });
  });

  it('quien no administra compras no consulta proveedores pendientes', () => {
    crear(['ALMACENISTA']);
    backend.expectNone(r => r.url === '/api/proveedores');
    expect(c()['totalAvisos']()).toBe(0);
  });

  it('el menú lateral ya no ofrece "Ver como dueño" a nadie de la empresa', () => {
    crear(['ADMIN_SISTEMA']);
    expect(html().textContent).not.toContain('Ver como dueño');
  });

  it('cada aviso lleva a su pantalla', () => {
    crear(['ADMIN_COMPRAS']);
    backend.expectOne(r => r.url === '/api/proveedores').flush(pagina([]));
    expect(c()['rutaDe']({ evento: 'COTIZACION_RECIBIDA' })).toBe('/app/cotizaciones');
    expect(c()['rutaDe']({ evento: 'PROVEEDOR_PENDIENTE' })).toBe('/app/proveedores');
    expect(c()['rutaDe']({ evento: 'PAGO_REGISTRADO' })).toBeUndefined();
  });
});
