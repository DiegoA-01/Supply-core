import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { CatalogoItem, EstadoReabastecimiento, ItemReabastecimiento, ProductoEnSede, Reabastecimiento, ResumenSede } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ROLES_CATALOGOS } from '../../core/navegacion/menu';
import { CatalogosProveedorService } from '../../core/servicios/catalogos.proveedor.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { SelectorSede } from '../../core/ui/selector-sede';

/** Texto, color y explicación de cada estado del plan. */
export const ESTADOS: Record<EstadoReabastecimiento, { texto: string; tono: string; ayuda: string }> = {
  URGENTE: { texto: 'Urgente', tono: 'rojo', ayuda: 'Aun pidiendo hoy, se acaba antes de que llegue el pedido.' },
  PEDIR_YA: { texto: 'Pedir hoy', tono: 'naranja', ayuda: 'Lo que hay no cubre el plazo de entrega más la seguridad.' },
  PRONTO: { texto: 'Pedir pronto', tono: 'ambar', ayuda: 'Hay que pedir en los próximos días hábiles.' },
  OK: { texto: 'Alcanza', tono: 'verde', ayuda: 'No hace falta pedir todavía.' },
  SIN_CONSUMO: { texto: 'Sin consumo', tono: 'gris', ayuda: 'No tuvo salidas: no hay con qué calcular.' },
};

/** Fila en edición del plazo propio de un producto. */
interface EdicionPlazo {
  id: number;
  plazo: number | null;
  seguridad: number | null;
}

/**
 * Plan de reabastecimiento: para cada producto, cuánto se consume, cuánto tarda el proveedor en días hábiles y hasta
 * cuándo se puede pedir sin quedarse sin stock. Todas las cifras (consumo, punto de pedido, fechas y cantidad) las
 * calcula el backend; aquí solo se filtran, se eligen y se dibujan.
 */
@Component({
  selector: 'sc-reabastecimiento',
  imports: [FormsModule, RouterLink, NumeroPipe, FechaPipe, PesosPipe, SelectorSede],
  templateUrl: './reabastecimiento.html',
  styleUrl: './reabastecimiento.scss',
})
export class PlanReabastecimiento implements OnInit {
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);
  private readonly catalogos = inject(CatalogosProveedorService);
  protected readonly sesion = inject(SesionService);

  protected readonly estados = Object.keys(ESTADOS) as EstadoReabastecimiento[];
  protected readonly datos = signal<Reabastecimiento | null>(null);
  protected readonly filtro = signal<EstadoReabastecimiento | 'PEDIR' | null>('PEDIR');
  protected readonly texto = signal('');
  protected readonly elegidos = signal<Set<number>>(new Set());
  protected readonly fechaRequerida = signal('');
  protected readonly creando = signal(false);
  protected readonly edicion = signal<EdicionPlazo | null>(null);
  protected readonly abierto = signal<number | null>(null);
  /** Ofertas confirmadas en los catálogos de proveedores para el producto abierto. */
  protected readonly ofertas = signal<CatalogoItem[]>([]);
  /** Ven las ofertas de catálogos los roles de compras con el módulo de proveedores activo. */
  protected readonly veOfertas = computed(() => this.sesion.tieneRol(ROLES_CATALOGOS) && this.sesion.tieneModulo('PROVEEDORES'));
  /** Sede del plan; {@code null} = toda la empresa. */
  protected readonly sede = signal<number | null>(null);

  /** El SOLICITANTE convierte el plan en una solicitud; el ADMIN_COMPRAS ajusta los plazos. */
  protected readonly puedePedir = computed(() => this.sesion.tieneRol(['SOLICITANTE']) && !this.sesion.soloLectura());
  protected readonly puedeAjustar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  protected readonly items = computed(() => {
    const f = this.filtro();
    const t = this.texto().trim().toLowerCase();
    return (this.datos()?.items ?? []).filter(i =>
      (f === null || (f === 'PEDIR' ? ['URGENTE', 'PEDIR_YA', 'PRONTO'].includes(i.estado!) : i.estado === f))
      && (!t || `${i.codigo} ${i.nombre} ${i.categoria}`.toLowerCase().includes(t)));
  });
  /** Productos que se pueden pasar a la solicitud: lo que queda por comprar después de un posible traslado. */
  protected readonly pedibles = computed(() => this.items().filter(i => Number(i.cantidadComprar) > 0));
  /** Comparación de sedes (solo en la vista de toda la empresa con dos o más sedes). */
  protected readonly comparacion = signal<ResumenSede[]>([]);
  /** Límites del producto abierto en la sede elegida. */
  protected readonly limiteSede = signal<{ productoId: number; minimo: number; maximo: number | null; actual?: ProductoEnSede } | null>(null);

  /** @param i producto; abre o cierra su detalle y trae sus ofertas en los catálogos */
  protected verDetalle(i: ItemReabastecimiento): void {
    const id = this.abierto() === i.productoId ? null : i.productoId!;
    this.abierto.set(id);
    this.limiteSede.set(null);
    this.ofertas.set([]);
    if (id !== null && this.veOfertas()) {
      this.catalogos.ofertasDe(id).subscribe({ next: l => this.ofertas.set(l), error: () => this.ofertas.set([]) });
    }
  }

  ngOnInit(): void {
    this.fechaRequerida.set(new Date().toISOString().slice(0, 10));
    this.cargar();
  }

  protected cargar(): void {
    if (this.sede() === null) {
      this.inventario.resumenSedes().subscribe({ next: l => this.comparacion.set(l.length > 1 ? l : []), error: () => this.comparacion.set([]) });
    } else {
      this.comparacion.set([]);
    }
    this.inventario.reabastecimiento(this.sede()).subscribe({
      next: d => {
        this.datos.set(d);
        // por defecto quedan elegidos los urgentes y los de pedir hoy
        this.elegidos.set(new Set((d.items ?? []).filter(i => (i.estado === 'URGENTE' || i.estado === 'PEDIR_YA')
          && Number(i.cantidadComprar) > 0).map(i => i.productoId!)));
        const primera = (d.items ?? []).find(i => i.estado === 'URGENTE' || i.estado === 'PEDIR_YA');
        if (primera?.llegadaSiPidoHoy) {
          this.fechaRequerida.set(primera.llegadaSiPidoHoy);
        }
      },
      error: e => {
        this.datos.set(null);
        this.avisos.error(e);
      },
    });
  }

  /** @param id sede elegida ({@code null} = toda la empresa); recalcula el plan */
  protected cambiarSede(id: number | null): void {
    this.sede.set(id);
    this.datos.set(null);
    this.cargar();
  }

  /** @param e estado; texto visible */
  protected etiqueta(e: EstadoReabastecimiento | undefined): string {
    return e ? ESTADOS[e].texto : '—';
  }

  /** @param e estado; clase de color del chip */
  protected tono(e: EstadoReabastecimiento | undefined): string {
    return e ? ESTADOS[e].tono : 'gris';
  }

  /** @param e estado; cuántos productos hay en ese estado (lo cuenta el backend) */
  protected cuenta(e: EstadoReabastecimiento): number {
    const d = this.datos();
    if (!d) {
      return 0;
    }
    return { URGENTE: d.urgentes, PEDIR_YA: d.pedirYa, PRONTO: d.pronto, OK: d.ok, SIN_CONSUMO: d.sinConsumo }[e] ?? 0;
  }

  /** @param id producto que se marca o desmarca para la solicitud */
  protected alternar(id: number): void {
    this.elegidos.update(s => {
      const n = new Set(s);
      if (n.has(id)) {
        n.delete(id);
      } else {
        n.add(id);
      }
      return n;
    });
  }

  /** @param i producto; {@code true} si el plazo histórico difiere del configurado en un día o más */
  protected historicoDistinto(i: ItemReabastecimiento): boolean {
    return i.plazoHistorico != null && Math.abs(Number(i.plazoHistorico) - (i.plazoEntregaDias ?? 0)) >= 1;
  }

  protected crearSolicitud(): void {
    const ids = [...this.elegidos()];
    if (!ids.length) {
      this.avisos.ok('Elige al menos un producto con cantidad para pedir.');
      return;
    }
    this.creando.set(true);
    this.inventario.borradorReabastecimiento(this.fechaRequerida(), ids, this.sede()).subscribe({
      next: s => {
        this.creando.set(false);
        this.avisos.ok(`${s.consecutivo} quedó en borrador. Revísala y envíala a aprobación.`);
        void this.router.navigate(['/app/solicitudes', s.id, 'editar']);
      },
      error: e => {
        this.creando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param i producto abierto; carga su mínimo y máximo en la sede elegida para poder editarlos */
  protected abrirLimites(i: ItemReabastecimiento): void {
    const sede = this.sede();
    if (sede === null) {
      return;
    }
    this.inventario.productoEnSedes(i.productoId!).subscribe({
      next: l => {
        const actual = l.find(x => x.sedeId === sede);
        this.limiteSede.set({ productoId: i.productoId!, minimo: Number(actual?.stockMinimo ?? 0),
          maximo: actual?.stockMaximo == null ? null : Number(actual.stockMaximo), actual });
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param campo límite; @param valor valor escrito */
  protected cambiarLimite(campo: 'minimo' | 'maximo', valor: unknown): void {
    const n = valor === '' || valor === null || valor === undefined ? null : Number(valor);
    this.limiteSede.update(l => (l ? { ...l, [campo]: campo === 'minimo' ? (n ?? 0) : n } : l));
  }

  protected guardarLimites(): void {
    const l = this.limiteSede();
    const sede = this.sede();
    if (!l || sede === null) {
      return;
    }
    if (l.minimo < 0 || (l.maximo !== null && l.maximo < l.minimo)) {
      this.avisos.ok('El mínimo no puede ser negativo y el máximo no puede ser menor que el mínimo.');
      return;
    }
    this.inventario.guardarLimitesSede(l.productoId, sede, l.minimo, l.maximo).subscribe({
      next: () => {
        this.limiteSede.set(null);
        this.avisos.ok('Límites de la sede guardados. El plan se recalculó.');
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param i producto cuyo plazo se va a ajustar */
  protected editarPlazo(i: ItemReabastecimiento): void {
    this.edicion.set({ id: i.productoId!, plazo: i.plazoPropio ? i.plazoEntregaDias! : null, seguridad: i.diasSeguridad ?? null });
  }

  protected guardarPlazo(): void {
    const e = this.edicion();
    if (!e) {
      return;
    }
    const valido = (v: number | null, max: number) => v === null || (Number.isInteger(v) && v >= 0 && v <= max);
    if (!valido(e.plazo, 120) || !valido(e.seguridad, 60)) {
      this.avisos.ok('El plazo va de 0 a 120 días hábiles y la seguridad de 0 a 60.');
      return;
    }
    this.inventario.cambiarPlazo(e.id, e.plazo, e.seguridad).subscribe({
      next: () => {
        this.edicion.set(null);
        this.avisos.ok('Plazo guardado. El plan se recalculó.');
        this.cargar();
      },
      error: err => this.avisos.error(err),
    });
  }

  /** @param campo dato del plazo; @param valor valor escrito (vacío = el de la empresa) */
  protected cambiarEdicion(campo: 'plazo' | 'seguridad', valor: unknown): void {
    const n = valor === '' || valor === null || valor === undefined ? null : Number(valor);
    this.edicion.update(e => (e ? { ...e, [campo]: n } : e));
  }
}
