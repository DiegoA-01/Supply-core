import { describe, expect, it } from 'vitest';

import { Tiquete } from '../../core/api/modelos';
import { htmlTiquete } from './tiquete';

const tiquete = (copia: boolean): Tiquete => ({
  copia, impresion: copia ? 2 : 1, impresoEn: '2026-11-20T10:00:00', empresa: 'Ferretería <Demo>', nit: '900123456-7', sede: 'Centro',
  impuestos: [{ tarifaPct: 19, base: 15546.22, impuesto: 2953.78 }],
  venta: {
    consecutivo: 'C1-000001', caja: 'Caja 1', confirmadaEn: '2026-11-20T09:00:00', cajero: 'Pedro', cliente: 'Consumidor final',
    clienteDocumento: 'CC 222222222222', subtotal: 18500, descuentoTotal: 0, total: 18500, redondeo: 0, totalAPagar: 18500, cambio: 1500,
    lineas: [{ nombre: 'Cable 12', cantidad: 5, unidad: 'M', precioUnitario: 3700, totalLinea: 18500 }],
    pagos: [{ medio: 'EFECTIVO', monto: 18500, recibido: 20000 }],
  },
});

describe('Tiquete de 80 mm', () => {
  it('trae el consecutivo, el NIT, el IVA discriminado y el cambio, en 80 mm', () => {
    const html = htmlTiquete(tiquete(false));
    expect(html).toContain('size: 80mm');
    expect(html).toContain('C1-000001');
    expect(html).toContain('NIT 900123456-7');
    expect(html).toContain('IVA 19 %');
    expect(html).toContain('Cambio');
    expect(html).not.toContain('COPIA');
  });

  it('la reimpresión dice COPIA y el texto de la empresa no se interpreta como HTML', () => {
    const html = htmlTiquete(tiquete(true));
    expect(html).toContain('COPIA 1');
    expect(html).toContain('Ferretería &lt;Demo&gt;');
  });
});
