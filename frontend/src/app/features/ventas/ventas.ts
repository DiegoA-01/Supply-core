import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EstadoVenta, LimiteDescuento, Pagina, Venta, VentaResumen } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { VentaService } from '../../core/servicios/venta.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { MEDIOS_VENTA } from '../pos/pos';
import { imprimirTiquete } from '../pos/tiquete';

/** Nombre de cada rol en los límites de descuento. */
const ROLES_LIMITE: Record<string, string> = { CAJERO: 'Cajero', SUPERVISOR_VENTAS: 'Supervisor de ventas', VENDEDOR: 'Vendedor' };

/**
 * Historial de ventas (spec 005): supervisor, dueño y auditor ven todas; el vendedor, las suyas. Detalle con líneas y
 * pagos, y reimpresión del tiquete (sale como COPIA y queda en la auditoría). El dueño fija aquí el descuento máximo de
 * cada rol. Anular y devolver llegan con la spec 006.
 */
@Component({
  selector: 'sc-ventas',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './ventas.html',
  styles: `
    .filtros { display: flex; flex-wrap: wrap; gap: 12px; padding: 14px 20px; border-bottom: 1px solid var(--sc-borde); align-items: flex-end;
      .campo { min-width: 150px; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .detalle { display: grid; gap: 12px; h2 { margin: 0; } }
    .totales { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 8px; margin: 0;
      dt { font-size: 12px; color: var(--sc-texto-2); } dd { margin: 0; font-weight: 600; } }
    .limites { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin: 12px 0; }
    tr.anulada td { color: var(--sc-texto-3); text-decoration: line-through; }
  `,
})
export class Ventas implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly medios = MEDIOS_VENTA;
  protected readonly pagina = signal<Pagina<VentaResumen> | null>(null);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly estado = signal<EstadoVenta | null>(null);
  protected readonly consecutivo = signal('');
  protected readonly detalle = signal<Venta | null>(null);
  protected readonly limites = signal<LimiteDescuento[]>([]);

  protected readonly esDueno = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly veLimites = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA', 'SUPERVISOR_VENTAS', 'AUDITOR']));

  ngOnInit(): void {
    this.buscar();
    if (this.veLimites()) {
      this.ventas.limites().subscribe({ next: l => this.limites.set(l), error: e => this.avisos.error(e) });
    }
  }

  /** @param pagina página */
  protected buscar(pagina = 0): void {
    this.ventas.buscar({ desde: this.desde() || null, hasta: this.hasta() || null, estado: this.estado(),
      consecutivo: this.consecutivo().trim() || null, pagina }).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /** @param v venta del historial */
  protected ver(v: VentaResumen): void {
    this.ventas.obtener(v.id!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  /** @param id venta; la reimpresión sale como COPIA */
  protected reimprimir(id: number): void {
    this.ventas.tiquete(id).subscribe({ next: t => imprimirTiquete(t), error: e => this.avisos.error(e) });
  }

  /** @param rol rol; @param valor porcentaje */
  protected cambiarLimite(rol: string, valor: number | null): void {
    this.limites.update(l => l.map(x => (x.rol === rol ? { ...x, descuentoMaximoPct: valor ?? 0 } : x)));
  }

  protected guardarLimites(): void {
    this.ventas.guardarLimites(this.limites()).subscribe({
      next: l => {
        this.limites.set(l);
        this.avisos.ok('Límites de descuento guardados.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param rol código @returns nombre */
  protected nombreRol(rol: string | undefined): string {
    return ROLES_LIMITE[rol ?? ''] ?? rol ?? '';
  }

  /** @param medio medio @returns texto */
  protected textoMedio(medio: string | undefined): string {
    return (this.medios as Record<string, string>)[medio ?? ''] ?? medio ?? '';
  }
}
