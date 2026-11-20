import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ClaseRotacion, Tablero, TableroVentas } from '../../core/api/modelos';
import { CompactoPipe, FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ReportesService } from '../../core/servicios/reportes.service';
import { ReportesVentasService } from '../../core/servicios/reportes-ventas.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { SelectorSede } from '../../core/ui/selector-sede';
import { ESTADOS } from '../inventario/reabastecimiento';

/** Texto y color de cada clase de rotación (los mismos de la pantalla de rotación). */
const CLASES: Record<ClaseRotacion, { texto: string; tono: string }> = {
  ALTA: { texto: 'Rota rápido', tono: 'verde' },
  MEDIA: { texto: 'Rotación normal', tono: 'gris' },
  BAJA: { texto: 'Rota lento', tono: 'ambar' },
  SIN_MOVIMIENTO: { texto: 'Sin movimiento', tono: 'rojo' },
  AGOTADO: { texto: 'Agotado', tono: 'rojo-oscuro' },
  SIN_ACTIVIDAD: { texto: 'Sin actividad', tono: 'claro' },
};

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];

/**
 * Tablero del dueño: cómo va el gasto de los últimos doce meses, cuánto dinero hay en inventario (y cuánto está
 * parado), qué se mueve más y menos, y qué hay que pedir. Todas las cifras, incluida la variación del mes y el saldo
 * por pagar, vienen calculadas del backend; aquí solo se dibujan (alto y ancho de las barras).
 */
@Component({
  selector: 'sc-tablero',
  imports: [RouterLink, PesosPipe, CompactoPipe, NumeroPipe, FechaPipe, SelectorSede],
  templateUrl: './tablero.html',
  styleUrl: './tablero.scss',
})
export class TableroDueno implements OnInit {
  private readonly reportes = inject(ReportesService);
  private readonly avisos = inject(Avisos);
  private readonly reportesVentas = inject(ReportesVentasService);
  private readonly sesion = inject(SesionService);

  /** Ventas de hoy y del mes (spec 009): solo con el módulo de ventas y para el dueño o auditoría. */
  protected readonly ventas = signal<TableroVentas | null>(null);
  protected readonly veVentas = computed(() =>
    this.sesion.tieneModulo('VENTAS') && this.sesion.tieneRol(['ADMIN_SISTEMA', 'AUDITOR']));

  protected readonly datos = signal<Tablero | null>(null);
  protected readonly error = signal(false);
  /** Sede del tablero; {@code null} = toda la empresa. */
  protected readonly sede = signal<number | null>(null);
  protected readonly clases = Object.keys(CLASES) as ClaseRotacion[];

  /** Barras del gasto por mes: el alto es proporcional al mes más alto (solo dibujo). */
  protected readonly barras = computed(() => {
    const meses = this.datos()?.gastoPorMes ?? [];
    const max = Math.max(1, ...meses.map(m => Number(m.total ?? 0)));
    return meses.map((m, i) => ({
      etiqueta: this.nombreMes(m.periodo),
      total: Number(m.total ?? 0),
      alto: (Number(m.total ?? 0) / max) * 100,
      actual: i === meses.length - 1,
    }));
  });

  /** Total de productos para repartir la barra de rotación (conteo, no dinero). */
  protected readonly totalClases = computed(() =>
    this.clases.reduce((s, c) => s + Number(this.datos()?.productosPorClase?.[c] ?? 0), 0));

  ngOnInit(): void {
    this.cargar();
    if (this.veVentas()) {
      this.reportesVentas.tablero().subscribe({ next: v => this.ventas.set(v), error: () => undefined });
    }
  }

  /** @param id sede elegida ({@code null} = toda la empresa); vuelve a pedir el tablero */
  protected cambiarSede(id: number | null): void {
    this.sede.set(id);
    this.datos.set(null);
    this.cargar();
  }

  private cargar(): void {
    this.error.set(false);
    this.reportes.tablero(this.sede()).subscribe({
      next: d => this.datos.set(d),
      error: e => {
        this.error.set(true);
        this.avisos.error(e);
      },
    });
  }

  /** @param periodo año-mes (2026-11); nombre corto del mes */
  protected nombreMes(periodo: string | undefined): string {
    if (!periodo) {
      return '';
    }
    const [anio, mes] = periodo.split('-');
    return `${MESES[Number(mes) - 1]}${mes === '01' ? ' ' + anio.slice(2) : ''}`;
  }

  /**
   * @param valor cifra de una lista
   * @param lista cifras de la misma lista
   * @return ancho de la barra en % respecto al mayor (solo dibujo)
   */
  protected ancho(valor: number | string | undefined, lista: (number | string | undefined)[]): number {
    const max = Math.max(1, ...lista.map(v => Number(v ?? 0)));
    return (Number(valor ?? 0) / max) * 100;
  }

  /** @param c clase; cuántos productos tiene */
  protected cuenta(c: ClaseRotacion): number {
    return Number(this.datos()?.productosPorClase?.[c] ?? 0);
  }

  /** @param c clase; parte de la barra en % (solo dibujo) */
  protected parte(c: ClaseRotacion): number {
    return this.totalClases() ? (this.cuenta(c) / this.totalClases()) * 100 : 0;
  }

  /** @param c clase; texto */
  protected textoClase(c: ClaseRotacion): string {
    return CLASES[c].texto;
  }

  /** @param c clase; color */
  protected tonoClase(c: ClaseRotacion): string {
    return CLASES[c].tono;
  }

  /** @param e estado de reabastecimiento; texto y color */
  protected estado(e: string | undefined): { texto: string; tono: string } {
    return e && e in ESTADOS ? ESTADOS[e as keyof typeof ESTADOS] : { texto: '—', tono: 'gris' };
  }

  /** @param lista items con total; cifras para calcular el ancho */
  protected totales(lista: { total?: number | string }[] | undefined): (number | string | undefined)[] {
    return (lista ?? []).map(i => i.total);
  }

  /** @param lista items con salidas; cifras para calcular el ancho */
  protected salidas(lista: { salidas?: number | string }[] | undefined): (number | string | undefined)[] {
    return (lista ?? []).map(i => i.salidas);
  }

  /** @param lista items con valor; cifras para calcular el ancho */
  protected valores(lista: { valorStock?: number | string | null }[] | undefined): (number | string | undefined)[] {
    return (lista ?? []).map(i => i.valorStock ?? 0);
  }
}
