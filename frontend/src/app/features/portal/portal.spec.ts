import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';

import { Ingreso } from '../ingreso/ingreso';
import { PortalOrdenes } from './portal-ordenes';
import { PortalPerfil } from './portal-perfil';
import { RegistroProveedor } from './registro-proveedor';

let backend: HttpTestingController;

function preparar(): void {
  sessionStorage.clear();
  TestBed.configureTestingModule({ providers: [provideRouter([{ path: '**', children: [] }]), provideHttpClient(), provideHttpClientTesting()] });
  backend = TestBed.inject(HttpTestingController);
}

const c = (f: ComponentFixture<unknown>) => f.componentInstance as Record<string, any>;
const texto = (f: ComponentFixture<unknown>) => (f.nativeElement as HTMLElement).textContent ?? '';

afterEach(() => backend.verify());

describe('Registro público del proveedor', () => {
  const categorias = [{ id: 2, nombre: 'Eléctrico', activo: true }, { id: 3, nombre: 'Ferretería', activo: true }];

  function abrir(empresa = 'Ferretería Centro'): ComponentFixture<RegistroProveedor> {
    preparar();
    const f = TestBed.createComponent(RegistroProveedor);
    f.componentRef.setInput('empresa', empresa);
    f.detectChanges();
    return f;
  }

  function llenar(f: ComponentFixture<RegistroProveedor>): void {
    const x = c(f);
    x['razonSocial'].set('Eléctricos del Norte SAS');
    x['nit'].set('900123456-7');
    x['nombreContacto'].set('Laura Gómez');
    x['correo'].set('laura@norte.co');
    x['password'].set('Clave2026ok');
    x['confirmacion'].set('Clave2026ok');
    x['alternar'](2);
  }

  it('pide las categorías con el código de la empresa codificado (lleva espacios y tildes)', () => {
    const f = abrir();
    const p = backend.expectOne(r => r.url.includes('/categorias'));

    expect(p.request.url).toBe('/api/portal/registro/Ferreter%C3%ADa%20Centro/categorias');
    expect(p.request.headers.has('Authorization')).toBe(false);
    p.flush(categorias);
    f.detectChanges();
    expect(texto(f)).toContain('Ferretería');
  });

  it('con un código que no existe avisa que el enlace no sirve', () => {
    const f = abrir('no-existe');
    backend.expectOne(r => r.url.includes('/categorias')).flush({ codigo: 'NO_ENCONTRADO', mensaje: 'no' }, { status: 404, statusText: 'Not Found' });
    f.detectChanges();

    expect(texto(f)).toContain('Enlace de registro no válido');
  });

  it('no envía con NIT mal escrito, sin categoría o con contraseñas distintas', () => {
    const f = abrir();
    backend.expectOne(r => r.url.includes('/categorias')).flush(categorias);
    llenar(f);

    c(f)['nit'].set('900.123.456');
    c(f)['registrar']();
    expect(c(f)['error']()).toContain('NIT');
    backend.expectNone(r => r.method === 'POST');

    c(f)['nit'].set('900123456-7');
    c(f)['categoriaIds'].set([]);
    c(f)['registrar']();
    expect(c(f)['error']()).toContain('categoría');

    c(f)['alternar'](2);
    c(f)['confirmacion'].set('otra');
    c(f)['registrar']();
    expect(c(f)['error']()).toContain('no coinciden');
    backend.expectNone(r => r.method === 'POST');
  });

  it('un registro válido manda todos los datos, borra la contraseña y explica que queda pendiente', () => {
    const f = abrir();
    backend.expectOne(r => r.url.includes('/categorias')).flush(categorias);
    llenar(f);
    c(f)['registrar']();

    const p = backend.expectOne(r => r.method === 'POST');
    expect(p.request.url).toBe('/api/portal/registro/Ferreter%C3%ADa%20Centro');
    expect(p.request.body).toEqual({ razonSocial: 'Eléctricos del Norte SAS', nit: '900123456-7', telefono: undefined, direccion: undefined,
      categoriaIds: [2], nombreContacto: 'Laura Gómez', correo: 'laura@norte.co', password: 'Clave2026ok' });
    p.flush({ proveedorId: 9, estado: 'PENDIENTE', mensaje: 'Registro recibido: queda pendiente de habilitación' });
    f.detectChanges();

    expect(c(f)['password']()).toBe('');
    expect(texto(f)).toContain('Registro enviado');
    expect(texto(f)).toContain('Hasta entonces no puedes ingresar');
  });

  it('un error del servidor (NIT o correo repetido) se muestra y se puede corregir', () => {
    const f = abrir();
    backend.expectOne(r => r.url.includes('/categorias')).flush(categorias);
    llenar(f);
    c(f)['registrar']();
    backend.expectOne(r => r.method === 'POST').flush({ codigo: 'VALIDACION', mensaje: 'Ya existe un proveedor con ese NIT' }, { status: 409, statusText: 'Conflict' });
    f.detectChanges();

    expect(texto(f)).toContain('Ya existe un proveedor con ese NIT');
    expect(c(f)['enviando']()).toBe(false);
  });
});

describe('Enlace al registro desde el ingreso', () => {
  it('lleva al registro con el código escrito; sin código pide escribirlo', async () => {
    preparar();
    const f = TestBed.createComponent(Ingreso);
    f.detectChanges();
    c(f)['registrarProveedor']();
    expect(c(f)['error']()).toContain('código de la empresa');

    c(f)['form'].controls.empresa.setValue('Ferretería Centro');
    c(f)['registrarProveedor']();
    await f.whenStable();
    expect(decodeURIComponent(TestBed.inject(Router).url)).toBe('/registro/Ferretería Centro');
  });
});

describe('Perfil del proveedor', () => {
  const proveedor = { id: 9, razonSocial: 'Eléctricos del Norte', nit: '900123', correoContacto: 'v@norte.co', estado: 'HABILITADO', scoreActual: 87.5, categorias: [{ id: 2, nombre: 'Eléctrico' }] };

  function abrir(): ComponentFixture<PortalPerfil> {
    preparar();
    const f = TestBed.createComponent(PortalPerfil);
    f.detectChanges();
    backend.expectOne('/api/portal/proveedor').flush(proveedor);
    backend.expectOne('/api/portal/proveedor/categorias').flush([{ id: 2, nombre: 'Eléctrico', activo: true }, { id: 3, nombre: 'Ferretería', activo: true }]);
    backend.expectOne(r => r.url === '/api/portal/proveedor/evaluaciones').flush({ contenido: [
      { id: 1, puntualidad: 90, cumplimientoCantidad: 100, calidad: 80, precio: 85, score: 88, creadoEn: '2026-11-10T10:00:00' }], pagina: 0, tamano: 10, totalElementos: 1, totalPaginas: 1 });
    f.detectChanges();
    return f;
  }

  it('muestra su estado, su score y las calificaciones que recibió', () => {
    const f = abrir();

    expect(texto(f)).toContain('Eléctricos del Norte');
    expect(texto(f)).toContain('Cómo te califican');
    expect(texto(f)).toContain('La razón social y el NIT los cambia la empresa');
  });

  it('editar manda contacto y categorías a su propia ficha y exige una categoría', () => {
    const f = abrir();
    c(f)['editar']();
    c(f)['telefono'].set('3001112222');
    c(f)['alternar'](3);
    c(f)['guardar']();

    const p = backend.expectOne('/api/portal/proveedor');
    expect(p.request.method).toBe('PUT');
    expect(p.request.body).toEqual({ correoContacto: 'v@norte.co', telefono: '3001112222', direccion: undefined, categoriaIds: [2, 3] });
    p.flush({ ...proveedor, telefono: '3001112222' });

    c(f)['editar']();
    c(f)['categoriaIds'].set([]);
    c(f)['guardar']();
    backend.expectNone('/api/portal/proveedor');
  });
});

describe('Órdenes del portal: lo que ya recibió el cliente', () => {
  it('muestra lo aceptado, lo rechazado y el motivo cuando hay recepciones', () => {
    preparar();
    const f = TestBed.createComponent(PortalOrdenes);
    f.detectChanges();
    backend.expectOne(r => r.url === '/api/portal/ordenes').flush({ contenido: [{ id: 9, consecutivo: 'OC-000009', estado: 'DESPACHADA', total: 1000000 }], pagina: 0, tamano: 50, totalElementos: 1, totalPaginas: 1 });
    backend.expectOne('/api/portal/ordenes/9').flush({ id: 9, consecutivo: 'OC-000009', estado: 'DESPACHADA', lineas: [], total: 1000000 });
    backend.expectOne('/api/portal/ordenes/9/pagos').flush({ pagos: [], total: 1000000, pagado: 0, saldo: 1000000 });
    backend.expectOne('/api/portal/ordenes/9/facturas').flush([]);
    backend.expectOne('/api/portal/ordenes/9/recepciones').flush({
      lineas: [{ ordenLineaId: 1, productoNombre: 'Cable', pedido: 100, aceptado: 90, rechazado: 10, faltante: 0 }],
      recepciones: [{ id: 1, recibidoEn: '2026-11-17T10:00:00', lineas: [{ id: 1, productoNombre: 'Cable', cantidadRechazada: 10, motivoRechazo: 'DANADO', observacionCalidad: 'Cubierta rota' }] }],
    });
    f.detectChanges();

    expect(texto(f)).toContain('Lo que el cliente ya recibió');
    expect(texto(f)).toContain('se rechazaron 10 de Cable');
    expect(texto(f)).toContain('Cubierta rota');
  });
});
