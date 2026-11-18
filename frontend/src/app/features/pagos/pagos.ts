import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Pagina, Pago, Proveedor } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { ReportesService } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { PagoAcciones } from '../ordenes/pago-acciones';

/**
 * Pagos de todas las órdenes: quién cobró, cuánto, con qué comprobante y cuáles faltan por documentar. Supply-Core no
 * mueve dinero, solo registra. Tesorería adjunta comprobantes y anula pagos con motivo; compras y auditoría consultan.
 * Los valores vienen del backend; la pantalla no suma ni calcula.
 */
@Component({
  selector: 'sc-pagos',
  imports: [FormsModule, RouterLink, Estado, EtiquetaPipe, FechaPipe, PesosPipe, PagoAcciones],
  templateUrl: './pagos.html',
  styleUrl: './pagos.scss',
})
export class Pagos implements OnInit {
  private readonly servicio = inject(OrdenesService);
  private readonly reportes = inject(ReportesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pagina = signal<Pagina<Pago> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly proveedores = signal<Proveedor[]>([]);
  protected readonly estado = signal<string | null>(null);
  protected readonly sinComprobante = signal(false);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMINISTRATIVO']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.reportes.proveedores('HABILITADO').subscribe({ next: p => this.proveedores.set(p.contenido ?? []), error: () => undefined });
    this.buscar(0);
  }

  /** @param p número de página desde 0 */
  protected buscar(p: number): void {
    this.pagina.set(null);
    this.numeroPagina.set(p);
    this.servicio.buscarPagos({
      estado: this.estado(),
      evidenciaPendiente: this.sinComprobante() ? true : null,
      proveedorId: this.proveedorId(),
      desde: this.desde() || null,
      hasta: this.hasta() || null,
    }, p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  protected limpiar(): void {
    this.estado.set(null);
    this.sinComprobante.set(false);
    this.proveedorId.set(null);
    this.desde.set('');
    this.hasta.set('');
    this.buscar(0);
  }
}
