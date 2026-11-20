import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { CotizacionVenta, ProductoParaVender, Turno } from '../../core/api/modelos';
import { SesionService } from '../../core/sesion/sesion.service';
import { PuntoDeVenta } from './pos';

const turno: Turno = { id: 4, cajaId: 1, caja: 'Caja 1', bodega: 'Principal', cajero: 'Pedro', estado: 'ABIERTO', baseApertura: 0, debeRetirar: false };
const cable: ProductoParaVender = { productoId: 7, codigo: 'CAB-12', codigoBarras: '770123', nombre: 'Cable 12', unidadMedidaId: 3, unidad: 'M', precio: 3700, stock: 20 };
const cotizacion = (extra: Partial<CotizacionVenta> = {}): CotizacionVenta => ({
  lineas: [{ productoId: 7, nombre: 'Cable 12', precioUnitario: 3700, totalLinea: 3700, pasaLimite: false, bajoMinimo: false }],
  subtotal: 3700, descuentoTotal: 0, impuestos: 590.76, total: 3700, redondeo: 0, totalAPagar: 3700, pagado: 0,
  requiereAutorizacionDescuento: false, requiereAutorizacionPrecio: false, ...extra,
});
const sesion = (roles: string[]) => ({ provide: SesionService,
  useValue: { tieneRol: (r: string[]) => r.some(x => roles.includes(x)), soloLectura: () => false, empresa: signal(null) } });

describe('Punto de venta', () => {
  let fixture: ComponentFixture<PuntoDeVenta>;
  let backend: HttpTestingController;
  const c = () => fixture.componentInstance as unknown as Record<string, any>;
  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  /** @param roles roles; @param conTurno si hay turno abierto; @param configurar si se arma el TestBed aquí */
  const crear = (roles: string[], conTurno = true, configurar = true) => {
    if (configurar) {
      TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(roles)] });
    }
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PuntoDeVenta);
    fixture.detectChanges();
    backend.expectOne('/api/clientes/consumidor-final').flush({ id: 1, nombre: 'Consumidor final', esConsumidorFinal: true });
    if (roles.some(r => r === 'CAJERO' || r === 'SUPERVISOR_VENTAS')) {
      const actual = backend.expectOne('/api/turnos/actual');
      if (conTurno) {
        actual.flush(turno);
        backend.expectOne(r => r.url === '/api/cajas').flush([{ id: 1, bodegaId: 9 }]);
      } else {
        actual.flush({ codigo: 'NO_ENCONTRADO' }, { status: 404, statusText: 'Not Found' });
      }
    }
    fixture.detectChanges();
  };
  /** Escanea un código y agrega el producto. */
  const escanear = () => {
    c()['texto'].set('770123');
    c()['leer']();
    const busqueda = backend.expectOne(r => r.url === '/api/ventas/productos');
    expect(busqueda.request.params.get('bodegaId')).toBe('9');
    busqueda.flush([cable]);
  };
  /** @param cot respuesta del servidor; deja pasar la pausa entre teclas y responde la cotización */
  const cotizar = (cot = cotizacion()) => {
    vi.advanceTimersByTime(300);
    const r = backend.expectOne('/api/ventas/cotizar');
    r.flush(cot);
    fixture.detectChanges();
    return r;
  };

  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.useRealTimers();
    backend.verify();
  });

  it('sin turno abierto lleva a Mi turno', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), sesion(['CAJERO'])] });
    const ir = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    crear(['CAJERO'], false, false);
    expect(ir).toHaveBeenCalledWith(['/app/caja/mi-turno']);
    expect(c()['listo']()).toBe(false);
  });

  it('Enter en el lector agrega el producto y los totales son los del servidor', () => {
    crear(['CAJERO']);
    escanear();
    const r = cotizar();
    expect(r.request.body.lineas).toEqual([{ productoId: 7, unidadMedidaId: 3, cantidad: 1, descuentoPct: 0 }]);
    expect(texto()).toContain('3.700');
    // la misma lectura otra vez suma una unidad, no una línea nueva
    escanear();
    expect(c()['carrito']().length).toBe(1);
    expect(c()['carrito']()[0].cantidad).toBe(2);
    cotizar(cotizacion({ total: 7400, totalAPagar: 7400 }));
  });

  it('F9 abre el cobro y el cambio lo calcula el servidor antes de confirmar', () => {
    crear(['CAJERO']);
    escanear();
    cotizar();
    c()['atajo'](new KeyboardEvent('keydown', { key: 'F9' }));
    expect(c()['cobrando']()).toBe(true);
    c()['cambiarPago'](0, 'monto', 5000);
    const r = cotizar(cotizacion({ cambio: 1300, pagado: 5000 }));
    expect(r.request.body.pagos).toEqual([{ medio: 'EFECTIVO', monto: 5000, ultimos4: undefined, referencia: undefined }]);
    expect(texto()).toContain('Cambio');
    expect(c()['puedeConfirmar']()).toBe(true);
  });

  it('si la red falla al cobrar, el reintento lleva la misma clave para no cobrar dos veces', () => {
    crear(['CAJERO']);
    escanear();
    cotizar(cotizacion({ cambio: 0 }));
    const clave = c()['clave']();
    c()['confirmar']();
    backend.expectOne('/api/ventas').error(new ProgressEvent('error'), { status: 0 });
    expect(c()['reintento']()).toBe(true);
    c()['confirmar']();
    const segundo = backend.expectOne('/api/ventas');
    expect(segundo.request.headers.get('Idempotency-Key')).toBe(clave);
    segundo.flush({ id: 50, consecutivo: 'C1-000001', totalAPagar: 3700, cambio: 0 });
    backend.expectOne('/api/ventas/50/tiquete').flush({ copia: false, venta: { consecutivo: 'C1-000001', lineas: [], pagos: [] } });
    backend.expectOne('/api/turnos/actual').flush(turno);
    // carrito nuevo, clave nueva
    expect(c()['vacio']()).toBe(true);
    expect(c()['clave']()).not.toBe(clave);
  });

  it('un descuento sobre el límite pide el PIN del supervisor antes de cobrar', () => {
    crear(['CAJERO']);
    escanear();
    cotizar(cotizacion({ cambio: 0, requiereAutorizacionDescuento: true }));
    c()['cobrando'].set(true);
    c()['confirmar']();
    backend.expectNone('/api/ventas');
    expect(c()['pin']()).toBe('DESCUENTO');
    c()['autorizado']('permiso-1');
    const venta = backend.expectOne('/api/ventas');
    expect(venta.request.body.autorizacionDescuento).toBe('permiso-1');
    venta.flush({ id: 51, consecutivo: 'C1-000002', totalAPagar: 3700, cambio: 0 });
    backend.expectOne('/api/ventas/51/tiquete').flush({ copia: false, venta: { consecutivo: 'C1-000002', lineas: [], pagos: [] } });
    backend.expectOne('/api/turnos/actual').flush(turno);
  });

  it('el vendedor no cobra: F9 guarda el pedido de venta', () => {
    crear(['VENDEDOR']);
    c()['texto'].set('770123');
    c()['leer']();
    backend.expectOne(r => r.url === '/api/ventas/productos').flush([cable]);
    cotizar();
    expect(texto()).toContain('Guardar pedido');
    c()['atajo'](new KeyboardEvent('keydown', { key: 'F9' }));
    const pedido = backend.expectOne('/api/pedidos-venta');
    expect(pedido.request.body.lineas[0].productoId).toBe(7);
    pedido.flush({ id: 3, consecutivo: 'PV-000003' });
    expect(c()['vacio']()).toBe(true);
  });

  it('Esc cierra el cobro y luego vacía el carrito, preguntando antes y nunca desde un campo', () => {
    const pregunta = vi.spyOn(window, 'confirm').mockReturnValue(true);
    crear(['CAJERO']);
    escanear();
    cotizar();
    c()['abrirCobro']();
    cotizar();
    c()['atajo'](new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(c()['cobrando']()).toBe(false);
    cotizar();
    // Esc escrito en un campo (el lector, una cantidad) no vacía el carrito
    const campo = document.createElement('input');
    document.body.appendChild(campo);
    campo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    expect(c()['vacio']()).toBe(false);
    campo.remove();
    c()['atajo'](new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(pregunta).toHaveBeenCalled();
    expect(c()['vacio']()).toBe(true);
    pregunta.mockRestore();
  });

  it('F9 dentro del cobro confirma y no borra los pagos escritos', () => {
    crear(['CAJERO']);
    escanear();
    cotizar();
    c()['abrirCobro']();
    c()['cambiarPago'](0, 'monto', 5000);
    cotizar(cotizacion({ cambio: 1300, pagado: 5000 }));
    c()['atajo'](new KeyboardEvent('keydown', { key: 'F9' }));
    const venta = backend.expectOne('/api/ventas');
    expect(venta.request.body.pagos[0].monto).toBe(5000);
    venta.flush({ id: 52, consecutivo: 'C1-000003', totalAPagar: 3700, cambio: 1300 });
    backend.expectOne('/api/ventas/52/tiquete').flush({ copia: false, venta: { consecutivo: 'C1-000003', lineas: [], pagos: [] } });
    backend.expectOne('/api/turnos/actual').flush(turno);
  });

  it('pendiente de reintento, el carrito queda fijo: ni Esc ni cambios mueven la clave', () => {
    crear(['CAJERO']);
    escanear();
    cotizar(cotizacion({ cambio: 0 }));
    const clave = c()['clave']();
    c()['confirmar']();
    backend.expectOne('/api/ventas').error(new ProgressEvent('error'), { status: 0 });
    c()['atajo'](new KeyboardEvent('keydown', { key: 'Escape' }));
    c()['cambiarCantidad'](0, 5);
    c()['quitar'](0);
    vi.advanceTimersByTime(300);
    backend.expectNone('/api/ventas/cotizar');
    expect(c()['carrito']()[0].cantidad).toBe(1);
    expect(c()['clave']()).toBe(clave);
    // descartar pregunta y solo entonces abre un carrito nuevo
    const pregunta = vi.spyOn(window, 'confirm').mockReturnValue(true);
    c()['descartar']();
    expect(c()['vacio']()).toBe(true);
    expect(c()['clave']()).not.toBe(clave);
    pregunta.mockRestore();
  });

  it('una cotización que llega tarde no pisa la más reciente', () => {
    crear(['CAJERO']);
    escanear();
    vi.advanceTimersByTime(300);
    const vieja = backend.expectOne('/api/ventas/cotizar');
    c()['cambiarCantidad'](0, 2);
    vi.advanceTimersByTime(300);
    const nueva = backend.expectOne('/api/ventas/cotizar');
    nueva.flush(cotizacion({ total: 7400, totalAPagar: 7400 }));
    vieja.flush(cotizacion());
    expect(c()['cotizacion']().totalAPagar).toBe(7400);
  });

  it('el cobro es de un solo medio: en efectivo pregunta con cuánto paga, y dividir el pago es aparte', () => {
    crear(['CAJERO']);
    escanear();
    cotizar();
    c()['abrirCobro']();
    cotizar();
    expect(c()['pagos']()[0].monto).toBeNull();
    expect(texto()).toContain('¿Con cuánto paga el cliente?');
    expect(texto()).not.toContain('Agregar otro medio');
    expect(c()['billetes']()).toEqual([5000, 10000, 20000]);
    c()['pagarExacto']();
    expect(cotizar().request.body.pagos).toEqual([{ medio: 'EFECTIVO', monto: 3700, ultimos4: undefined, referencia: undefined }]);
    // con tarjeta se cobra el total que da el servidor (sin redondeo del efectivo)
    c()['elegirMedio']('TARJETA_DEBITO');
    const r = cotizar(cotizacion({ total: 3710, totalAPagar: 3710 }));
    expect(r.request.body.pagos[0].medio).toBe('TARJETA_DEBITO');
    expect(cotizar(cotizacion({ total: 3710, totalAPagar: 3710, cambio: 0 })).request.body.pagos[0].monto).toBe(3710);
    expect(texto()).toContain('Se cobra el total por Tarjeta débito');
    c()['dividir']();
    fixture.detectChanges();
    expect(c()['dividido']()).toBe(true);
    expect(texto()).toContain('Agregar otro medio');
    c()['unSoloMedio']();
    cotizar(cotizacion({ total: 3710, totalAPagar: 3710, cambio: 0 }));
    expect(c()['dividido']()).toBe(false);
  });

  it('mientras se escribe sugiere productos, se eligen con las flechas y Enter los agrega', () => {
    crear(['CAJERO']);
    const cemento: ProductoParaVender = { ...cable, productoId: 8, codigo: 'CEM-50', codigoBarras: '770999', nombre: 'Cemento gris 50 kg', precio: 49500 };
    c()['escribir']('c');
    c()['escribir']('ce');
    vi.advanceTimersByTime(250);
    const req = backend.expectOne(r => r.url === '/api/ventas/productos');
    expect(req.request.params.get('q')).toBe('ce');
    req.flush([cemento, cable]);
    fixture.detectChanges();
    expect(texto()).toContain('Cemento gris 50 kg');
    expect(c()['marcado']()).toBe(0);
    c()['teclaLector'](new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    expect(c()['marcado']()).toBe(1);
    c()['teclaLector'](new KeyboardEvent('keydown', { key: 'Enter' }));
    expect(c()['carrito']()[0].productoId).toBe(7);
    expect(c()['opciones']().length).toBe(0);
    cotizar();
    // sin coincidencias lo dice
    c()['escribir']('zzz');
    vi.advanceTimersByTime(250);
    backend.expectOne(r => r.url === '/api/ventas/productos').flush([]);
    fixture.detectChanges();
    expect(texto()).toContain('No hay productos que coincidan con «zzz»');
  });

  it('el panel de cliente explica cuándo identificar al cliente y qué hacer si no aparece', () => {
    crear(['CAJERO']);
    expect(texto()).toContain('¿A quién le vende?');
    expect(texto()).toContain('Consumidor final');
    expect(texto()).toContain('pide la factura a su nombre');
    expect(texto()).toContain('Crear cliente nuevo');
    c()['abrirClientes']();
    c()['buscarCliente'].set('99887766');
    c()['buscarClientes']();
    backend.expectOne(r => r.url === '/api/clientes').flush({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
    fixture.detectChanges();
    expect(texto()).toContain('No hay ningún cliente con «99887766»');
    expect(texto()).toContain('Seguir como consumidor final');
    c()['creandoCliente'].set(true);
    fixture.detectChanges();
    expect(texto()).toContain('Escriba el tipo y número de documento');
  });
});
