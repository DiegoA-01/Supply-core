import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EstadoCuenta, Gasto, GastoItem, ProveedorRanking } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, PesosPipe, PuntajePipe } from '../../core/formato/formato';
import { ReportesService, iso } from '../../core/servicios/reportes.service';
import { SelectorSede } from '../../core/ui/selector-sede';
import { Avisos } from '../../core/ui/avisos';

type Pestana = 'gasto' | 'proveedores' | 'cuenta';

/** Vista 8 · Reportes de solo lectura (UC-15). Todos los totales vienen del backend. */
@Component({
  selector: 'sc-reportes',
  imports: [FormsModule, RouterLink, Estado, PesosPipe, PuntajePipe, FechaPipe, SelectorSede],
  templateUrl: './reportes.html',
  styleUrl: './reportes.scss',
})
export class Reportes implements OnInit {
  private readonly servicio = inject(ReportesService);
  private readonly avisos = inject(Avisos);

  protected readonly pestana = signal<Pestana>('cuenta');
  protected readonly desde = signal(iso(new Date(new Date().getFullYear(), new Date().getMonth() - 2, 1)));
  protected readonly hasta = signal(iso(new Date()));
  protected readonly gasto = signal<Gasto | null>(null);
  protected readonly ranking = signal<ProveedorRanking[] | null>(null);
  protected readonly opciones = signal<ProveedorRanking[]>([]);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly cuenta = signal<EstadoCuenta | null>(null);
  protected readonly cargando = signal(false);
  /** Sede de los reportes de gasto y proveedores; {@code null} = toda la empresa. */
  protected readonly sede = signal<number | null>(null);
  /** Comparación de compras entre sedes (solo en toda la empresa con dos o más sedes con compras). */
  protected readonly porSede = signal<GastoItem[]>([]);

  ngOnInit(): void {
    // Los proveedores con órdenes en el último año sirven de lista para el estado de cuenta.
    const hoy = new Date();
    this.servicio.ranking(iso(new Date(hoy.getFullYear() - 1, hoy.getMonth(), 1)), iso(hoy)).subscribe({
      next: l => {
        this.opciones.set([...l].sort((a, b) => (a.razonSocial ?? '').localeCompare(b.razonSocial ?? '')));
        if (l.length && !this.proveedorId()) {
          this.proveedorId.set(l[0]!.proveedorId ?? null);
          this.consultar();
        }
      },
      error: () => undefined,
    });
  }

  /** @param p pestaña */
  protected cambiar(p: Pestana): void {
    this.pestana.set(p);
    this.consultar();
  }

  protected consultar(): void {
    const desde = this.desde();
    const hasta = this.hasta();
    if (!desde || !hasta) {
      return;
    }
    this.cargando.set(true);
    const listo = () => this.cargando.set(false);
    const error = (e: unknown) => {
      listo();
      this.avisos.error(e);
    };
    switch (this.pestana()) {
      case 'gasto':
        this.servicio.gasto(desde, hasta, this.sede()).subscribe({ next: g => { this.gasto.set(g); listo(); }, error });
        if (this.sede() === null) {
          this.servicio.gastoPorSede(desde, hasta).subscribe({ next: l => this.porSede.set(l.length > 1 ? l : []), error: () => this.porSede.set([]) });
        } else {
          this.porSede.set([]);
        }
        break;
      case 'proveedores':
        this.servicio.ranking(desde, hasta, this.sede()).subscribe({ next: r => { this.ranking.set(r); listo(); }, error });
        break;
      case 'cuenta': {
        const id = this.proveedorId();
        if (!id) {
          listo();
          return;
        }
        this.servicio.estadoCuenta(id, desde, hasta).subscribe({ next: c => { this.cuenta.set(c); listo(); }, error });
        break;
      }
    }
  }
}
