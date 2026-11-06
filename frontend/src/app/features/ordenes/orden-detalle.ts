import { LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { Factura, Orden, OrdenLinea, PagosOrden, RegistrarPago, ResumenRecepcion } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/**
 * Vista 5 · Orden, pagos y cruce de tres vías. Los montos esperados, la diferencia y el saldo los calcula el
 * backend; aquí solo se muestran y se registran decisiones.
 */
@Component({
  selector: 'sc-orden-detalle',
  imports: [FormsModule, RouterLink, LowerCasePipe, Estado, EtiquetaPipe, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './orden-detalle.html',
  styleUrl: './orden-detalle.scss',
})
export class OrdenDetalle implements OnInit {
  private readonly servicio = inject(OrdenesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Id de la orden (de la ruta). */
  readonly id = input.required<string>();

  protected readonly orden = signal<Orden | null>(null);
  protected readonly facturas = signal<Factura[]>([]);
  protected readonly pagos = signal<PagosOrden | null>(null);
  protected readonly recepcion = signal<ResumenRecepcion | null>(null);
  protected readonly motivo = signal('');
  protected readonly trabajando = signal(false);
  protected readonly mostrarPago = signal(false);
  protected readonly pago = signal<RegistrarPago>({ tipo: 'ANTICIPO', metodo: 'TRANSFERENCIA', valor: 0, fechaPago: iso(new Date()), referencia: '' });

  /** Factura vigente (la última no anulada). */
  protected readonly factura = computed(() => this.facturas().find(f => f.estado !== 'ANULADA') ?? null);
  protected readonly enRevision = computed(() => this.factura()?.estado === 'EN_REVISION');
  protected readonly puedeAprobar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly puedePagar = computed(() => this.sesion.tieneRol(['ADMINISTRATIVO']) && !this.sesion.soloLectura());
  protected readonly tolerancia = computed(() => this.sesion.empresa()?.toleranciaFacturaPct);

  ngOnInit(): void {
    this.cargar();
  }

  /** @param l línea de la orden; avance de la recepción calculado por el backend */
  protected avance(l: OrdenLinea) {
    return this.recepcion()?.lineas?.find(a => a.ordenLineaId === l.id);
  }

  protected aprobarFactura(): void {
    this.decidirFactura(f => this.servicio.aprobarFactura(f, this.motivo().trim()), 'Factura aprobada: ya se puede registrar el pago final.');
  }

  protected anularFactura(): void {
    this.decidirFactura(f => this.servicio.anularFactura(f, this.motivo().trim()), 'Factura anulada. Pide al proveedor una nota crédito o una factura nueva.');
  }

  protected registrarPago(): void {
    const o = this.orden();
    const p = this.pago();
    if (!o?.id || !p.valor || p.valor <= 0 || !p.fechaPago) {
      this.avisos.ok('Escribe el valor y la fecha del pago.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.registrarPago(o.id, { ...p, referencia: p.referencia?.trim() }).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.mostrarPago.set(false);
        this.avisos.ok('Pago registrado. Adjunta el comprobante para cerrar la evidencia.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param campo campo del pago por registrar */
  protected cambiarPago<K extends keyof RegistrarPago>(campo: K, valor: RegistrarPago[K]): void {
    this.pago.update(p => ({ ...p, [campo]: valor }));
  }

  protected descargarPdf(): void {
    const o = this.orden();
    if (!o?.id) {
      return;
    }
    this.servicio.pdf(o.id).subscribe({
      next: b => descargar(b, `${o.consecutivo}-v${o.version}.pdf`),
      error: e => this.avisos.error(e),
    });
  }

  private decidirFactura(accion: (id: number) => ReturnType<OrdenesService['aprobarFactura']>, mensaje: string): void {
    const f = this.factura();
    if (!f?.id) {
      return;
    }
    if (!this.motivo().trim()) {
      this.avisos.ok('Escribe el motivo: es obligatorio.');
      return;
    }
    this.trabajando.set(true);
    accion(f.id).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.motivo.set('');
        this.avisos.ok(mensaje);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    const id = Number(this.id());
    forkJoin({
      orden: this.servicio.obtener(id),
      facturas: this.servicio.facturas(id).pipe(catchError(() => of([] as Factura[]))),
      pagos: this.servicio.pagos(id).pipe(catchError(() => of(null))),
      recepcion: this.servicio.recepciones(id).pipe(catchError(() => of(null))),
    }).subscribe({
      next: r => {
        this.orden.set(r.orden);
        this.facturas.set([...r.facturas].sort((a, b) => (b.id ?? 0) - (a.id ?? 0)));
        this.pagos.set(r.pagos);
        this.recepcion.set(r.recepcion);
      },
      error: e => this.avisos.error(e),
    });
  }
}
