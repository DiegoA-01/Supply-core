import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AgrupacionVenta, CajaReporte, Cuadre, Pagina, VentaReporte } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ReportesVentasService } from '../../core/servicios/reportes-ventas.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos, descargar } from '../../core/ui/avisos';

/** Nombre de cada agrupación del reporte de ventas. */
export const AGRUPACIONES: Record<AgrupacionVenta, string> = {
  DIA: 'Día', SEMANA: 'Semana', MES: 'Mes', PRODUCTO: 'Producto', CATEGORIA: 'Categoría', CLIENTE: 'Cliente',
  VENDEDOR: 'Vendedor', CAJERO: 'Cajero', CAJA: 'Caja', MEDIO_PAGO: 'Medio de pago',
};

/** Roles que ven todo (caja y cuadre); el cajero y el vendedor solo ven sus ventas. */
const SUPERVISION = ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'];

/**
 * Reportes de ventas y caja y cuadre diario (spec 009). Las ventas son netas (lo confirmado menos lo devuelto) y todas
 * las cifras, incluido el margen, vienen del backend; la pantalla solo las muestra.
 */
@Component({
  selector: 'sc-reportes-ventas',
  imports: [FormsModule, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './reportes-ventas.html',
  styleUrl: './reportes-ventas.scss',
})
export class ReportesVentas implements OnInit {
  private readonly servicio = inject(ReportesVentasService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly agrupaciones = AGRUPACIONES;
  protected readonly ordenAgrupaciones = Object.keys(AGRUPACIONES) as AgrupacionVenta[];
  protected readonly pestana = signal<'ventas' | 'caja' | 'cuadre'>('ventas');
  protected readonly desde = signal<string>(primerDiaDelMes());
  protected readonly hasta = signal<string>(hoy());
  protected readonly agrupar = signal<AgrupacionVenta>('DIA');
  protected readonly ventas = signal<VentaReporte | null>(null);
  protected readonly caja = signal<CajaReporte | null>(null);
  protected readonly cuadres = signal<Pagina<Cuadre> | null>(null);
  protected readonly soloDescuadres = signal(true);
  protected readonly cargando = signal(false);

  protected readonly veCaja = computed(() => this.sesion.tieneRol(SUPERVISION));
  protected readonly veCuadre = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA', 'AUDITOR']));
  protected readonly puedeRevisar = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly porMedio = computed(() => this.ventas()?.agrupar === 'MEDIO_PAGO');

  ngOnInit(): void {
    this.cargarVentas();
  }

  /** @param p pestaña elegida; carga sus datos la primera vez */
  protected elegir(p: 'ventas' | 'caja' | 'cuadre'): void {
    this.pestana.set(p);
    if (p === 'caja' && !this.caja()) {
      this.cargarCaja();
    } else if (p === 'cuadre' && !this.cuadres()) {
      this.cargarCuadres();
    }
  }

  /** Vuelve a pedir la pestaña visible con los filtros actuales. */
  protected consultar(): void {
    if (this.pestana() === 'caja') {
      this.cargarCaja();
    } else if (this.pestana() === 'cuadre') {
      this.cargarCuadres();
    } else {
      this.cargarVentas();
    }
  }

  protected cargarVentas(): void {
    this.cargando.set(true);
    this.servicio.ventas(this.desde(), this.hasta(), this.agrupar()).subscribe({
      next: r => {
        this.cargando.set(false);
        this.ventas.set(r);
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected cargarCaja(): void {
    this.servicio.caja(this.desde(), this.hasta()).subscribe({ next: r => this.caja.set(r), error: e => this.avisos.error(e) });
  }

  protected cargarCuadres(): void {
    this.servicio.cuadres(this.soloDescuadres()).subscribe({ next: r => this.cuadres.set(r), error: e => this.avisos.error(e) });
  }

  /** @param fecha día a revisar otra vez (después de corregir) */
  protected revisar(fecha: string | undefined): void {
    if (!fecha) {
      return;
    }
    this.servicio.revisar(fecha).subscribe({
      next: c => {
        this.avisos.ok(c.cuadra ? `El ${fecha} ya cuadra.` : `El ${fecha} sigue sin cuadrar.`);
        this.cargarCuadres();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param formato archivo que se descarga, con los mismos filtros de la pantalla */
  protected exportar(formato: 'xlsx' | 'pdf'): void {
    const peticion = this.pestana() === 'caja'
      ? this.servicio.exportarCaja(formato, this.desde(), this.hasta())
      : this.servicio.exportarVentas(formato, this.desde(), this.hasta(), this.agrupar());
    peticion.subscribe({
      next: b => descargar(b, `reporte-${this.pestana()}-${this.desde()}-a-${this.hasta()}.${formato}`),
      error: e => this.avisos.error(e),
    });
  }

  /** @param valor diferencia; tono del número */
  protected tono(valor: number | null | undefined): string {
    return valor == null || valor === 0 ? '' : valor < 0 ? 'rojo' : 'ambar';
  }
}

/** @returns hoy en ISO (fecha local) */
function hoy(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

/** @returns primer día del mes en ISO */
function primerDiaDelMes(): string {
  return `${hoy().slice(0, 8)}01`;
}
