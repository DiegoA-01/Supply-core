import { Pipe, PipeTransform } from '@angular/core';

/**
 * Formato colombiano (es-CO). Solo da formato a lo que devuelve el backend: aquí no se calcula dinero ni stock.
 */
const PESOS = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0, maximumFractionDigits: 2 });
const COMPACTO = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', notation: 'compact', maximumFractionDigits: 1 });
const NUMERO = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 4 });
const DECIMAL = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/** "$ 1.234.567" */
export function pesos(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : PESOS.format(valor).replace(/\s/g, ' ');
}

/** "28" o "150,5" (cantidades y porcentajes, sin decimales sobrantes). */
export function numero(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? '—' : NUMERO.format(valor);
}

/**
 * Lee una fecha del backend. Una fecha sin hora ({@code 2026-11-05}) se toma en la zona local para que no se
 * corra un día.
 */
function aFecha(valor: string): Date {
  return /^\d{4}-\d{2}-\d{2}$/.test(valor) ? new Date(`${valor}T00:00:00`) : new Date(valor);
}

@Pipe({ name: 'pesos' })
export class PesosPipe implements PipeTransform {
  /** @param valor monto del backend */
  transform(valor: number | null | undefined): string {
    return pesos(valor);
  }
}

@Pipe({ name: 'compacto' })
export class CompactoPipe implements PipeTransform {
  /** @param valor monto del backend; "$ 19,8 M" */
  transform(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : COMPACTO.format(valor).replace(/\s/g, ' ');
  }
}

@Pipe({ name: 'numero' })
export class NumeroPipe implements PipeTransform {
  /** @param valor cantidad o porcentaje del backend */
  transform(valor: number | null | undefined): string {
    return numero(valor);
  }
}

@Pipe({ name: 'puntaje' })
export class PuntajePipe implements PipeTransform {
  /** @param valor puntaje del análisis; "91,4" */
  transform(valor: number | null | undefined): string {
    return valor === null || valor === undefined ? '—' : DECIMAL.format(valor);
  }
}

@Pipe({ name: 'fecha' })
export class FechaPipe implements PipeTransform {
  /**
   * @param valor fecha ISO del backend
   * @param tipo  {@code larga} (05 nov 2026), {@code corta} (05 nov) o {@code hora} (05 nov, 17:00)
   */
  transform(valor: string | null | undefined, tipo: 'larga' | 'corta' | 'hora' = 'larga'): string {
    if (!valor) {
      return '—';
    }
    const f = aFecha(valor);
    const dia = `${f.getDate()}`.padStart(2, '0');
    const base = `${dia} ${MESES[f.getMonth()]}`;
    if (tipo === 'corta') {
      return base;
    }
    if (tipo === 'hora') {
      return `${base}, ${`${f.getHours()}`.padStart(2, '0')}:${`${f.getMinutes()}`.padStart(2, '0')}`;
    }
    return `${base} ${f.getFullYear()}`;
  }
}

/**
 * Horas transcurridas desde una fecha (para mostrar cuánto lleva esperando algo).
 *
 * @param valor fecha ISO
 */
export function horasDesde(valor: string | null | undefined): number | null {
  if (!valor) {
    return null;
  }
  return Math.max(0, Math.floor((Date.now() - aFecha(valor).getTime()) / 3_600_000));
}

/** Iniciales de un nombre: "Laura Gómez" → "LG". */
export function iniciales(nombre: string | null | undefined): string {
  return (nombre ?? '').split(/\s+/).filter(Boolean).slice(0, 2).map(p => p[0]!.toUpperCase()).join('');
}
