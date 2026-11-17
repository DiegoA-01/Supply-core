import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { Cotizacion, Procesamiento } from '../../core/api/modelos';
import { Extraccion } from './extraccion';

const cotizacion: Cotizacion = {
  id: 9, proveedorRazonSocial: 'Eléctricos del Norte', origen: 'ARCHIVO',
  lineas: [{ id: 31, rfqLineaId: 5, productoCodigo: 'CAB-12', cantidadOfertada: 10, unidadOfertada: 'm', factorConversion: 1, precioUnitarioOfertado: 2500 }],
};

const proceso = (extra: Partial<Procesamiento> = {}): Procesamiento => ({
  id: 1, cotizacionId: 9, estado: 'REQUIERE_REVISION', modelo: 'gemini-2.5-flash', intentos: 1, umbralConfianza: 0.85,
  alertas: ['TOTAL_NO_CUADRA: el total impreso en el documento (27988.80) no cuadra con el calculado (27968.40).'],
  campos: [
    { id: 100, campo: 'totalImpreso', valorExtraido: '27988.80', confianza: 0, critico: true, pendiente: true },
    { id: 101, cotizacionLineaId: 31, campo: 'precioUnitario', valorExtraido: '2500', confianza: 0.62, critico: true, pendiente: true },
    { id: 102, cotizacionLineaId: 31, campo: 'unidad', valorExtraido: 'm', confianza: 0.99, critico: false, pendiente: false },
  ],
  ...extra,
});

describe('Extraccion (revisión de la lectura con IA)', () => {
  let fixture: ComponentFixture<Extraccion>;
  let backend: HttpTestingController;

  const crear = (editar: boolean) => {
    fixture = TestBed.createComponent(Extraccion);
    fixture.componentRef.setInput('cotizacion', cotizacion);
    fixture.componentRef.setInput('puedeEditar', editar);
    fixture.detectChanges();
    backend.expectOne('/api/cotizaciones/9/extraccion').flush(proceso());
    fixture.detectChanges();
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('muestra la alerta de total que no cuadra con su explicación y el total impreso', () => {
    crear(true);
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('El total del documento no cuadra con los renglones');
    expect(texto).toContain('27988.80');
    expect(texto).toContain('27968.40');
    expect(texto).toContain('se pide revisión bajo 85 %');
  });

  it('muestra solo los datos pendientes y nombra la línea por el código del producto', () => {
    crear(true);
    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(filas.length).toBe(2);
    expect(texto).toContain('CAB-12');
    expect(texto).toContain('Precio unitario');
    expect(texto).toContain('62 %');
  });

  it('confirma un dato con el valor de la caja y avisa que cambió el análisis', () => {
    crear(true);
    let cambios = 0;
    fixture.componentInstance.cambio.subscribe(() => cambios++);
    const botones = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('tbody button'));

    botones[1]!.click();

    const peticion = backend.expectOne('/api/cotizaciones/9/extraccion/campos/101');
    expect(peticion.request.method).toBe('PATCH');
    expect(peticion.request.body).toEqual({ valor: '2500' });
    peticion.flush(proceso({ estado: 'COMPLETADO', alertas: [], campos: [] }));
    expect(cambios).toBe(1);
  });

  it('sin permiso de edición no muestra botones para corregir ni reintentar', () => {
    crear(false);

    expect((fixture.nativeElement as HTMLElement).querySelectorAll('button').length).toBe(0);
  });

  it('digitar a mano manda las líneas de la cotización sin el nombre del producto', () => {
    crear(true);
    fixture.componentInstance['alternarDigitacion']();
    fixture.componentInstance['guardarDigitacion']();

    const peticion = backend.expectOne('/api/cotizaciones/9/extraccion/lineas');
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body.lineas).toEqual([
      { rfqLineaId: 5, cantidadOfertada: 10, unidadOfertada: 'm', factorConversion: 1, precioUnitarioOfertado: 2500, descuentoPct: 0, impuestoPct: 0 },
    ]);
    peticion.flush(proceso({ estado: 'COMPLETADO', alertas: [], campos: [] }));
  });
});
