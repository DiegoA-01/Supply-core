import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../../core/sesion/sesion.service';
import { SolicitudForm } from './solicitud-form';

const producto = (id: number, nombre: string) => ({ id, codigo: 'P' + id, nombre, costoReferencia: 4500, stockTotal: 10, unidadMedida: { id: 1, codigo: 'UND' } });
const pagina = (contenido: unknown[]) => ({ contenido, pagina: 0, tamano: 10, totalElementos: contenido.length, totalPaginas: 1 });

describe('Nueva solicitud: elegir productos', () => {
  let fixture: ComponentFixture<SolicitudForm>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;

  const crear = (roles: string[]) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting(),
        { provide: SesionService, useValue: { tieneRol: (r?: string[]) => !r?.length || r.some(x => roles.includes(x)) } }],
    });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SolicitudForm);
    fixture.detectChanges();
  };
  const productos = () => backend.expectOne(r => r.url === '/api/productos');

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
  });

  it('al poner el cursor en el campo ya trae el catálogo, sin escribir nada', () => {
    crear(['SOLICITANTE']);
    c()['abrirLista']();
    vi.advanceTimersByTime(250);
    const p = productos();
    expect(p.request.params.has('texto')).toBe(false);
    p.flush(pagina([producto(1, 'Cemento gris 50 kg'), producto(2, 'Tornillo 1 pulgada')]));

    expect(c()['resultados']().length).toBe(2);
    expect(c()['listaAbierta']()).toBe(true);
  });

  it('si varias palabras no coinciden prueba con la primera y avisa que son parecidos', () => {
    crear(['SOLICITANTE']);
    c()['buscar']('tornillo 3 octavos');
    vi.advanceTimersByTime(250);
    productos().flush(pagina([]));
    const segunda = productos();
    expect(segunda.request.params.get('texto')).toBe('tornillo');
    segunda.flush(pagina([producto(2, 'Tornillo 1 pulgada')]));

    expect(c()['parecidos']()).toBe(true);
    expect(c()['resultados']().length).toBe(1);
  });

  it('sin ninguna coincidencia queda el aviso, no un vacío mudo', () => {
    crear(['SOLICITANTE']);
    c()['buscar']('xyz');
    vi.advanceTimersByTime(250);
    productos().flush(pagina([]));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No hay productos que coincidan');
    expect(fixture.nativeElement.textContent).toContain('Pide a Compras que lo cree');
  });

  it('agregar un producto lo pone en la solicitud con cantidad 1 y cierra la lista', () => {
    crear(['SOLICITANTE']);
    c()['agregar'](producto(1, 'Cemento gris 50 kg'));

    expect(c()['lineas']().length).toBe(1);
    expect(c()['lineas']()[0].cantidad).toBe(1);
    expect(c()['listaAbierta']()).toBe(false);
  });

  it('quien administra compras crea el producto desde la misma pantalla y queda agregado', () => {
    crear(['ADMIN_COMPRAS']);
    c()['abrirCrear']();
    backend.expectOne(r => r.url === '/api/categorias').flush([{ id: 5, nombre: 'Eléctrico' }]);
    backend.expectOne(r => r.url === '/api/unidades-medida').flush([{ id: 1, codigo: 'UND', nombre: 'Unidad' }]);
    c()['nuevoCodigo'].set('CAB-14');
    c()['nuevoNombre'].set('Cable 14 AWG');
    c()['nuevaCategoria'].set(5);
    c()['nuevaUnidad'].set(1);
    c()['nuevoCosto'].set(2500);
    c()['crearProducto']();

    const p = backend.expectOne('/api/productos');
    expect(p.request.method).toBe('POST');
    expect(p.request.body).toMatchObject({ codigo: 'CAB-14', nombre: 'Cable 14 AWG', categoriaId: 5, unidadMedidaId: 1, costoReferencia: 2500 });
    p.flush(producto(9, 'Cable 14 AWG'));

    expect(c()['lineas']().map((l: any) => l.producto.id)).toEqual([9]);
    expect(c()['creando']()).toBe(false);
  });

  it('quien no administra compras no ve el botón de crear', () => {
    crear(['SOLICITANTE']);
    expect(c()['puedeCrear']()).toBe(false);
  });
});
