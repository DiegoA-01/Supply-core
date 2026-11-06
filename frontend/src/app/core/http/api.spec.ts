import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { codigoError, mensajeError, parametros } from './api';

describe('api', () => {
  it('omite los filtros vacíos', () => {
    const p = parametros({ estado: 'APROBADA', texto: '  ', centroCostoId: null, size: 50 });
    expect(p.keys()).toEqual(['estado', 'size']);
  });

  it('muestra el mensaje y los detalles del ErrorResponse del backend', () => {
    const e = new HttpErrorResponse({
      status: 409,
      error: { codigo: 'PRESUPUESTO_EXCEDIDO', mensaje: 'Supera el presupuesto', detalles: ['Disponible $ 100'] },
    });
    expect(mensajeError(e)).toBe('Supera el presupuesto: Disponible $ 100');
    expect(codigoError(e)).toBe('PRESUPUESTO_EXCEDIDO');
  });

  it('explica la falta de conexión', () => {
    expect(mensajeError(new HttpErrorResponse({ status: 0 }))).toContain('No hay conexión');
  });
});
