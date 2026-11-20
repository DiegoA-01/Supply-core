import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ClienteCartera, DetalleCartera, ResumenCartera } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CarteraService } from '../../core/servicios/cartera.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { htmlRecibo, imprimirHtml } from '../pos/tiquete';

/**
 * Cartera (spec 007): quién compra a crédito, cuánto debe y desde cuándo (al día, 1–30, 31–60, 61–90 y más de 90
 * días). Un cliente en mora queda bloqueado para crédito; el supervisor o el dueño puede habilitarlo hasta una fecha con
 * un motivo, aunque deba (decisión de la usuaria). El cupo se cambia en Clientes. Todos los saldos vienen del backend.
 */
@Component({
  selector: 'sc-cartera',
  imports: [FormsModule, RouterLink, PesosPipe, FechaPipe],
  templateUrl: './cartera.html',
  styleUrl: './cartera.scss',
})
export class Cartera implements OnInit {
  private readonly cartera = inject(CarteraService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly resumen = signal<ResumenCartera | null>(null);
  protected readonly filtro = signal<'todos' | 'mora' | 'bloqueados'>('todos');
  protected readonly texto = signal('');
  protected readonly detalle = signal<DetalleCartera | null>(null);
  protected readonly habilitando = signal(false);
  protected readonly hasta = signal(this.enDias(7));
  protected readonly motivo = signal('');

  /** Habilitan clientes en mora el supervisor de ventas y el dueño. */
  protected readonly decide = computed(() => this.sesion.tieneRol(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly clientes = computed(() => {
    const t = this.texto().trim().toLowerCase();
    return (this.resumen()?.clientes ?? []).filter(c =>
      (this.filtro() === 'todos' || (this.filtro() === 'mora' ? c.enMora : c.bloqueado))
      && (!t || `${c.nombre} ${c.documento}`.toLowerCase().includes(t)));
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cartera.resumen().subscribe({ next: r => this.resumen.set(r), error: e => this.avisos.error(e) });
  }

  /** @param c cliente; abre sus cuentas y abonos */
  protected ver(c: ClienteCartera): void {
    this.habilitando.set(false);
    this.cartera.detalle(c.clienteId!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  protected habilitar(): void {
    const d = this.detalle()!;
    this.cartera.habilitar(d.cliente!.clienteId!, this.hasta(), this.motivo().trim()).subscribe({
      next: () => {
        this.avisos.ok(`${d.cliente!.nombre} puede comprar a crédito hasta el ${this.hasta()}.`);
        this.habilitando.set(false);
        this.motivo.set('');
        this.refrescar(d.cliente!.clienteId!);
      },
      error: e => this.avisos.error(e),
    });
  }

  protected quitar(): void {
    const d = this.detalle()!;
    this.cartera.quitarExcepcion(d.cliente!.clienteId!).subscribe({
      next: () => {
        this.avisos.ok('Excepción retirada.');
        this.refrescar(d.cliente!.clienteId!);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param a abono del detalle; reimprime su recibo */
  protected recibo(a: NonNullable<DetalleCartera['abonos']>[number]): void {
    imprimirHtml(htmlRecibo(a));
  }

  /** @param id cliente; recarga su detalle y el resumen */
  private refrescar(id: number): void {
    this.cargar();
    this.cartera.detalle(id).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  /** @param dias días desde hoy @returns fecha aaaa-mm-dd */
  private enDias(dias: number): string {
    const f = new Date();
    f.setDate(f.getDate() + dias);
    return `${f.getFullYear()}-${String(f.getMonth() + 1).padStart(2, '0')}-${String(f.getDate()).padStart(2, '0')}`;
  }
}
