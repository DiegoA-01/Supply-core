import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CentroCosto, Presupuesto } from '../../core/api/modelos';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ComprasService } from '../../core/servicios/compras.service';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

const MESES = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

/**
 * Presupuestos por centro de costo y mes. Lo comprometido, lo disponible y el porcentaje de uso los calcula el backend
 * con las solicitudes y órdenes vigentes; aquí solo se definen los montos. Al crear uno ya existente para ese mes, el
 * sistema avisa. Al editar solo cambia el monto.
 */
@Component({
  selector: 'sc-presupuestos',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe],
  templateUrl: './presupuestos.html',
  styleUrl: './presupuestos.scss',
})
export class Presupuestos implements OnInit {
  private readonly servicio = inject(ConfiguracionService);
  private readonly compras = inject(ComprasService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly meses = MESES;
  protected readonly anios = (() => {
    const a = new Date().getFullYear();
    return [a - 1, a, a + 1, a + 2];
  })();

  protected readonly lista = signal<Presupuesto[] | null>(null);
  protected readonly centros = signal<CentroCosto[]>([]);
  protected readonly centroFiltro = signal<number | null>(null);
  protected readonly anioFiltro = signal<number>(new Date().getFullYear());

  protected readonly abierto = signal(false);
  protected readonly editando = signal<Presupuesto | null>(null);
  protected readonly centroId = signal<number | null>(null);
  protected readonly anio = signal<number>(new Date().getFullYear());
  protected readonly mes = signal<number>(new Date().getMonth() + 1);
  protected readonly monto = signal<number | null>(null);
  protected readonly trabajando = signal(false);

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.compras.centros().subscribe({ next: l => this.centros.set(l.filter(c => c.activo)), error: () => undefined });
    this.cargar();
  }

  /** @param id centro de costo; nombre para mostrar */
  protected centro(id: number | undefined): string {
    return this.centros().find(c => c.id === id)?.nombre ?? `#${id}`;
  }

  /** @param m número de mes (1 a 12); nombre del mes */
  protected nombreMes(m: number | undefined): string {
    return m ? MESES[m - 1] ?? String(m) : '';
  }

  protected cargar(): void {
    this.lista.set(null);
    this.servicio.presupuestos({ centroCostoId: this.centroFiltro(), anio: this.anioFiltro() }).subscribe({
      next: l => this.lista.set([...l].sort((a, b) => (a.anio ?? 0) - (b.anio ?? 0) || (a.mes ?? 0) - (b.mes ?? 0))),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.centroId.set(this.centroFiltro() ?? this.centros()[0]?.id ?? null);
    this.anio.set(this.anioFiltro());
    this.mes.set(new Date().getMonth() + 1);
    this.monto.set(null);
    this.abierto.set(true);
  }

  /** @param p presupuesto que se edita (solo cambia el monto) */
  protected editar(p: Presupuesto): void {
    this.editando.set(p);
    this.monto.set(p.monto ?? null);
    this.abierto.set(true);
  }

  protected guardar(): void {
    if (this.monto() == null || this.monto()! < 0) {
      this.avisos.ok('Escribe el monto del presupuesto.');
      return;
    }
    const p = this.editando();
    if (!p && !this.centroId()) {
      this.avisos.ok('Elige el centro de costo.');
      return;
    }
    this.trabajando.set(true);
    (p?.id
      ? this.servicio.editarPresupuesto(p.id, this.monto()!)
      : this.servicio.crearPresupuesto({ centroCostoId: this.centroId()!, anio: this.anio(), mes: this.mes(), monto: this.monto()! })
    ).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.abierto.set(false);
        this.avisos.ok(p ? 'Presupuesto actualizado.' : 'Presupuesto creado.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param p presupuesto; clase de color según cuánto va usado (solo estilo) */
  protected tono(p: Presupuesto): string {
    const u = p.usoPct ?? 0;
    return u >= 100 ? 'rojo' : u >= 80 ? 'ambar' : 'verde';
  }
}
