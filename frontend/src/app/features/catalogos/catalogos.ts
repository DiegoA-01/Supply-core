import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CatalogoItem, EstadoEquivalencia, Oportunidad, Pagina } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { CatalogosProveedorService } from '../../core/servicios/catalogos.proveedor.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { BuscadorProducto } from '../../core/ui/buscador-producto';
import { ESTADOS } from '../inventario/reabastecimiento';

/** Texto y color de cada estado de equivalencia. */
const EQUIVALENCIAS: Record<EstadoEquivalencia, { texto: string; tono: string }> = {
  SIN_REVISAR: { texto: 'Sin producto', tono: 'gris' },
  SUGERIDA: { texto: 'Sugerida', tono: 'ambar' },
  CONFIRMADA: { texto: 'Confirmada', tono: 'verde' },
  DESCARTADA: { texto: 'Descartada', tono: 'gris' },
};

/**
 * Catálogos de proveedores: oportunidades (más barato que el último costo o que hay que pedir) y el catálogo completo
 * de todos los proveedores, donde compras confirma a qué producto corresponde cada artículo. Los proveedores no ven
 * nada de esto.
 */
@Component({
  selector: 'sc-catalogos',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './catalogos.html',
  styleUrl: './catalogos.scss',
})
export class CatalogosProveedores implements OnInit {
  private readonly servicio = inject(CatalogosProveedorService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pestana = signal<'oportunidades' | 'catalogo'>('oportunidades');
  protected readonly oportunidades = signal<Oportunidad[] | null>(null);
  protected readonly pagina = signal<Pagina<CatalogoItem> | null>(null);
  protected readonly estado = signal<EstadoEquivalencia | null>(null);
  protected readonly texto = signal('');
  protected readonly editando = signal<number | null>(null);
  protected readonly buscador = new BuscadorProducto(this.inventario);
  protected readonly equivalencias = Object.keys(EQUIVALENCIAS) as EstadoEquivalencia[];

  /** Confirman equivalencias el jefe de compras y el comprador. */
  protected readonly decide = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.cargarOportunidades();
  }

  /** @param p pestaña */
  protected cambiar(p: 'oportunidades' | 'catalogo'): void {
    this.pestana.set(p);
    if (p === 'catalogo' && !this.pagina()) {
      this.buscar();
    }
  }

  protected cargarOportunidades(): void {
    this.servicio.oportunidades().subscribe({ next: l => this.oportunidades.set(l), error: e => this.avisos.error(e) });
  }

  protected buscar(): void {
    this.servicio.buscar(null, this.estado(), this.texto()).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /** @param c artículo; confirma el producto sugerido tal cual */
  protected confirmar(c: CatalogoItem): void {
    this.enviar(c.id!, 'CONFIRMADA', c.productoId ?? null);
  }

  /** @param c artículo; abre el buscador para elegir otro producto */
  protected cambiarProducto(c: CatalogoItem): void {
    this.editando.set(c.id!);
    this.buscador.escribir('');
  }

  /** @param c artículo; confirma con el producto elegido en el buscador */
  protected confirmarElegido(c: CatalogoItem): void {
    const id = this.buscador.id();
    if (!id) {
      this.avisos.ok('Elige el producto de la lista.');
      return;
    }
    this.enviar(c.id!, 'CONFIRMADA', id);
  }

  /** @param c artículo que no nos interesa */
  protected descartar(c: CatalogoItem): void {
    this.enviar(c.id!, 'DESCARTADA', null);
  }

  private enviar(id: number, estado: EstadoEquivalencia, productoId: number | null): void {
    this.servicio.decidir(id, estado, productoId).subscribe({
      next: r => {
        this.editando.set(null);
        this.pagina.update(p => (p ? { ...p, contenido: (p.contenido ?? []).map(x => (x.id === r.id ? r : x)) } : p));
        this.avisos.ok(estado === 'CONFIRMADA' ? 'Equivalencia confirmada.' : 'Artículo descartado.');
        this.cargarOportunidades();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param e estado; texto */
  protected textoEquivalencia(e: EstadoEquivalencia | undefined): string {
    return e ? EQUIVALENCIAS[e].texto : '—';
  }

  /** @param e estado; color */
  protected tonoEquivalencia(e: EstadoEquivalencia | undefined): string {
    return e ? EQUIVALENCIAS[e].tono : 'gris';
  }

  /** @param e estado del plan; texto y color */
  protected plan(e: string | null | undefined): { texto: string; tono: string } | null {
    return e && e in ESTADOS ? ESTADOS[e as keyof typeof ESTADOS] : null;
  }
}
