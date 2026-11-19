import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Categoria, ClaseRotacion, ItemRotacion, Rotacion } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { ReportesService, iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';
import { SelectorSede } from '../../core/ui/selector-sede';

/** Períodos rápidos; "otro" deja escribir las fechas. */
type Periodo = 30 | 60 | 90 | 180 | 365 | 'otro';
type Orden = 'salidas' | 'cobertura' | 'sinSalida' | 'stock' | 'valor' | 'nombre';

/** Texto y color de cada clase de rotación. */
const CLASES: Record<ClaseRotacion, { texto: string; tono: string; ayuda: string }> = {
  ALTA: { texto: 'Rota rápido', tono: 'verde', ayuda: 'El stock alcanza pocos días al ritmo del período.' },
  MEDIA: { texto: 'Rotación normal', tono: 'gris', ayuda: 'El stock alcanza un tiempo razonable.' },
  BAJA: { texto: 'Rota lento', tono: 'ambar', ayuda: 'Hay mucho más stock del que se consume.' },
  SIN_MOVIMIENTO: { texto: 'Sin movimiento', tono: 'rojo', ayuda: 'Hay stock pero no salió nada en el período: dinero parado.' },
  AGOTADO: { texto: 'Agotado', tono: 'rojo', ayuda: 'Se vendió o consumió y hoy no queda nada: hay que reponer.' },
  SIN_ACTIVIDAD: { texto: 'Sin actividad', tono: 'gris', ayuda: 'Sin stock y sin salidas en el período.' },
};

/**
 * Rotación de productos: qué sale más, qué sale menos, cuántos días alcanza el stock y qué lleva tiempo sin moverse.
 * Es un tablero para el dueño del negocio. Todas las cifras (salidas, días de cobertura, fechas de agotamiento, dinero
 * parado) las calcula el backend; aquí solo se ordena, se filtra, se cuenta y se dibuja.
 */
@Component({
  selector: 'sc-rotacion',
  imports: [FormsModule, RouterLink, NumeroPipe, PesosPipe, FechaPipe, SelectorSede],
  templateUrl: './rotacion.html',
  styleUrl: './rotacion.scss',
})
export class RotacionProductos implements OnInit {
  private readonly reportes = inject(ReportesService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);

  protected readonly periodos: { valor: Periodo; texto: string }[] = [
    { valor: 30, texto: '30 días' }, { valor: 60, texto: '60 días' }, { valor: 90, texto: '3 meses' },
    { valor: 180, texto: '6 meses' }, { valor: 365, texto: '12 meses' }, { valor: 'otro', texto: 'Otras fechas' },
  ];
  protected readonly clases = Object.keys(CLASES) as ClaseRotacion[];

  protected readonly datos = signal<Rotacion | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly cargando = signal(false);
  protected readonly periodo = signal<Periodo>(90);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly categoriaId = signal<number | null>(null);
  /** Sede del análisis; {@code null} = toda la empresa. */
  protected readonly sede = signal<number | null>(null);

  // Tabla completa
  protected readonly texto = signal('');
  protected readonly claseFiltro = signal<ClaseRotacion | null>(null);
  protected readonly orden = signal<Orden>('salidas');
  protected readonly descendente = signal(true);

  protected readonly items = computed(() => this.datos()?.items ?? []);
  /** Los que más salen: los 10 primeros con salidas (la lista ya viene de más a menos). */
  protected readonly masSalen = computed(() => this.items().filter(i => num(i.salidas) > 0).slice(0, 10));
  /** Los que menos salen: de los que sí tuvieron salidas, los 10 de menor salida (sin repetir a los que ya salen arriba). */
  protected readonly menosSalen = computed(() => this.items().filter(i => num(i.salidas) > 0).slice(10).slice(-10).reverse());
  protected readonly porAgotarse = computed(() => this.items().filter(i => i.porAgotarse)
    .sort((a, b) => num(a.diasCobertura) - num(b.diasCobertura)).slice(0, 10));
  protected readonly agotados = computed(() => this.items().filter(i => i.clase === 'AGOTADO').slice(0, 10));
  /** Sin movimiento: primero los que más dinero tienen parado y, si no hay costo, los que más días llevan sin salir. */
  protected readonly sinMovimiento = computed(() => this.items().filter(i => i.clase === 'SIN_MOVIMIENTO')
    .sort((a, b) => num(b.valorStock) - num(a.valorStock) || num(b.diasSinSalida) - num(a.diasSinSalida)).slice(0, 10));
  protected readonly topSalidas = computed(() => Math.max(1, ...this.masSalen().map(i => num(i.salidas))));
  protected readonly cuenta = computed(() => {
    const por: Record<string, number> = {};
    for (const i of this.items()) {
      por[i.clase ?? ''] = (por[i.clase ?? ''] ?? 0) + 1;
    }
    return por;
  });
  protected readonly filas = computed(() => {
    const t = this.texto().trim().toLowerCase();
    const c = this.claseFiltro();
    const clave = this.orden();
    const signo = this.descendente() ? -1 : 1;
    return this.items()
      .filter(i => (!t || `${i.codigo} ${i.nombre}`.toLowerCase().includes(t)) && (!c || i.clase === c))
      .sort((a, b) => signo * (clave === 'nombre' ? (a.nombre ?? '').localeCompare(b.nombre ?? '') : valor(a, clave) - valor(b, clave)));
  });

  ngOnInit(): void {
    this.inventario.categorias().subscribe({ next: l => this.categorias.set(l), error: () => undefined });
    this.cargar();
  }

  /** @param p período elegido */
  protected elegirPeriodo(p: Periodo): void {
    this.periodo.set(p);
    if (p !== 'otro') {
      this.cargar();
    }
  }

  protected cargar(): void {
    const p = this.periodo();
    const hasta = p === 'otro' ? this.hasta() : iso(new Date());
    const desdeFecha = new Date();
    if (p !== 'otro') {
      desdeFecha.setDate(desdeFecha.getDate() - (p - 1));
    }
    const desde = p === 'otro' ? this.desde() : iso(desdeFecha);
    if (p === 'otro' && (!desde || !hasta)) {
      this.avisos.ok('Escribe las dos fechas.');
      return;
    }
    this.cargando.set(true);
    this.reportes.rotacion(desde, hasta, this.categoriaId(), this.sede()).subscribe({
      next: r => {
        this.datos.set(r);
        this.cargando.set(false);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param clave columna por la que se ordena la tabla; repetir la columna invierte el orden */
  protected ordenarPor(clave: Orden): void {
    if (this.orden() === clave) {
      this.descendente.update(d => !d);
    } else {
      this.orden.set(clave);
      this.descendente.set(clave !== 'nombre' && clave !== 'cobertura');
    }
  }

  /** @param i producto; ancho de su barra frente al que más salió (solo dibujo) */
  protected ancho(i: ItemRotacion): number {
    return Math.max(2, (num(i.salidas) / this.topSalidas()) * 100);
  }

  /** @param c clase; texto para mostrar */
  protected texto_(c: ClaseRotacion | undefined): string {
    return c ? CLASES[c].texto : '';
  }

  /** @param c clase; color del chip */
  protected tono(c: ClaseRotacion | undefined): string {
    return c ? CLASES[c].tono : 'gris';
  }

  /** @param c clase; explicación corta */
  protected ayuda(c: ClaseRotacion): string {
    return CLASES[c].ayuda;
  }

  /** @param c clase; ancho del tramo en la barra de distribución */
  protected parte(c: ClaseRotacion): number {
    const total = this.items().length || 1;
    return ((this.cuenta()[c] ?? 0) / total) * 100;
  }

  /** @param i producto; texto de "cuánto le alcanza" */
  protected alcanza(i: ItemRotacion): string {
    if (i.diasCobertura == null) {
      return 'sin salidas';
    }
    const d = num(i.diasCobertura);
    return d <= 0 ? 'agotado' : d >= 365 ? 'más de un año' : `${Math.round(d)} días`;
  }

  /** @param i producto; frase completa: "le alcanza 6 días", "ya se agotó" o "sin salidas en el período" */
  protected frase(i: ItemRotacion): string {
    if (i.diasCobertura == null) {
      return 'sin salidas en el período';
    }
    return num(i.diasCobertura) <= 0 ? 'ya se agotó' : `le alcanza ${this.alcanza(i)}`;
  }

  /** @param i producto; hace cuánto salió por última vez */
  protected ultima(i: ItemRotacion): string {
    if (i.diasSinSalida == null) {
      return 'nunca ha salido';
    }
    return i.diasSinSalida === 0 ? 'hoy' : i.diasSinSalida === 1 ? 'hace 1 día' : `hace ${i.diasSinSalida} días`;
  }
}

/** @param v número que el backend manda como número o texto; 0 si no hay */
function num(v: number | string | null | undefined): number {
  return v == null ? 0 : Number(v);
}

/** @param i producto; @param clave columna; valor numérico para ordenar (lo que no tiene valor va al final) */
function valor(i: ItemRotacion, clave: Exclude<Orden, 'nombre'>): number {
  switch (clave) {
    case 'salidas': return num(i.salidas);
    case 'stock': return num(i.stock);
    case 'valor': return i.valorStock == null ? -1 : num(i.valorStock);
    case 'cobertura': return i.diasCobertura == null ? Number.MAX_SAFE_INTEGER : num(i.diasCobertura);
    case 'sinSalida': return i.diasSinSalida == null ? Number.MAX_SAFE_INTEGER : num(i.diasSinSalida);
  }
}
