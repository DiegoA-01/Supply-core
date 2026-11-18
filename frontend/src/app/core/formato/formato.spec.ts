import { describe, expect, it } from 'vitest';

import { FechaPipe, horasDesde, iniciales, numero, pesos } from './formato';
import { etiqueta, nombreRol, tono } from './etiquetas';

describe('formato es-CO', () => {
  it('muestra pesos con punto de miles y sin decimales sobrantes', () => {
    expect(pesos(1150730)).toBe('$ 1.150.730');
    expect(pesos(345219.5)).toBe('$ 345.219,5');
    expect(pesos(null)).toBe('—');
  });

  it('muestra cantidades sin ,00', () => {
    expect(numero(28)).toBe('28');
    expect(numero(150.5)).toBe('150,5');
  });

  it('formatea fechas como en el mockup sin correr el día', () => {
    const f = new FechaPipe();
    expect(f.transform('2026-11-05')).toBe('05 nov 2026');
    expect(f.transform('2026-10-29T19:40:00', 'hora')).toBe('29 oct, 19:40');
    expect(f.transform('2026-10-29', 'corta')).toBe('29 oct');
    expect(f.transform(null)).toBe('—');
  });

  it('cuenta horas transcurridas y saca iniciales', () => {
    const haceTresHoras = new Date(Date.now() - 3 * 3_600_000 - 60_000).toISOString();
    expect(horasDesde(haceTresHoras)).toBe(3);
    expect(horasDesde(undefined)).toBeNull();
    expect(iniciales('Laura Gómez Ruiz')).toBe('LG');
  });
});

describe('etiquetas', () => {
  it('traduce los estados del backend a texto legible con su color', () => {
    expect(etiqueta('RECIBIDA_TOTAL')).toBe('Recibida total');
    expect(tono('RECIBIDA_TOTAL')).toBe('verde');
    expect(etiqueta('EN_REVISION')).toBe('En revisión');
    expect(tono('EN_REVISION')).toBe('ambar');
  });

  it('un estado nuevo sin texto definido sale legible igual', () => {
    expect(etiqueta('ESTADO_NUEVO_DEL_BACKEND')).toBe('Estado nuevo del backend');
    expect(tono('ESTADO_NUEVO_DEL_BACKEND')).toBe('gris');
  });

  it('nombra los roles', () => {
    expect(nombreRol('ALMACENISTA')).toBe('Bodega');
  });
});
