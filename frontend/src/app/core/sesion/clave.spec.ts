import { describe, expect, it } from 'vitest';

import { PATRON_CLAVE } from './clave';

describe('PATRON_CLAVE', () => {
  it('acepta lo mismo que el backend: 8 a 72 caracteres con letra y número', () => {
    expect(PATRON_CLAVE.test('Prueba2026')).toBe(true);
    expect(PATRON_CLAVE.test('abc12345')).toBe(true);
  });

  it('rechaza contraseñas cortas, sin número o sin letra', () => {
    expect(PATRON_CLAVE.test('Ab12')).toBe(false);
    expect(PATRON_CLAVE.test('SoloLetras')).toBe(false);
    expect(PATRON_CLAVE.test('12345678')).toBe(false);
    expect(PATRON_CLAVE.test('a1'.repeat(37))).toBe(false);
  });
});
