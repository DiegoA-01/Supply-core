import { LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { Factura, FacturaNueva, Orden, OrdenLinea, PagosOrden, RegistrarPago, ResumenRecepcion } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { PagoAcciones } from './pago-acciones';
import { SesionService } from '../../core/sesion/sesion.service';

/**
 * Vista 5 · Orden, pagos y cruce de tres vías. Los montos esperados, la diferencia y el saldo los calcula el
 * backend; aquí solo se muestran y se registran decisiones.
 */
@Component({
  selector: 'sc-orden-detalle',
  imports: [FormsModule, RouterLink, LowerCasePipe, Estado, EtiquetaPipe, PesosPipe, NumeroPipe, FechaPipe, PagoAcciones],
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
  protected readonly puedeRecruzar = computed(() => this.sesion.tieneRol(['ADMINISTRATIVO']) && !this.sesion.soloLectura());
  protected readonly puedeAprobar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly puedePagar = computed(() => this.sesion.tieneRol(['ADMINISTRATIVO']) && !this.sesion.soloLectura());
  protected readonly tolerancia = computed(() => this.sesion.empresa()?.toleranciaFacturaPct);
  /** Tesorería registra la factura cuando ya entró mercancía y no hay una vigente. */
  protected readonly puedeFacturar = computed(() => {
    const e = this.orden()?.estado;
    return this.sesion.tieneRol(['ADMINISTRATIVO']) && !this.sesion.soloLectura() && !this.factura()
      && (e === 'RECIBIDA_PARCIAL' || e === 'RECIBIDA_TOTAL' || e === 'CERRADA');
  });
  protected readonly facturaNueva = signal<FacturaNueva>({ numero: '', fechaFactura: iso(new Date()), subtotal: 0, impuestos: 0, total: 0 });
  private readonly archivoFactura = signal<File | null>(null);

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

  /** @param campo dato de la factura; @param valor valor tal como viene impreso */
  protected cambiarFactura<K extends keyof FacturaNueva>(campo: K, valor: FacturaNueva[K]): void {
    this.facturaNueva.update(f => ({ ...f, [campo]: valor }));
  }

  /** @param evento cambio del selector de archivos */
  protected elegirFactura(evento: Event): void {
    this.archivoFactura.set((evento.target as HTMLInputElement).files?.[0] ?? null);
  }

  protected registrarFactura(): void {
    const o = this.orden();
    const f = this.facturaNueva();
    if (!o?.id || !f.numero.trim() || !f.fechaFactura || !(f.total > 0)) {
      this.avisos.ok('Completa número, fecha y montos de la factura.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.registrarFactura(o.id, { ...f, numero: f.numero.trim() }).subscribe({
      next: r => {
        const archivo = this.archivoFactura();
        if (archivo && r.id) {
          this.servicio.archivoFactura(r.id, archivo).subscribe({ error: e => this.avisos.error(e) });
        }
        this.trabajando.set(false);
        this.avisos.ok(r.estado === 'CUADRADA'
          ? 'Factura registrada: cuadra con lo recibido.'
          : 'Factura registrada, pero no cuadra con lo recibido: queda en revisión para compras.');
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

  protected enviarPorCorreo(): void {
    const o = this.orden();
    if (!o?.id) {
      return;
    }
    this.servicio.pdfPorCorreo(o.id).subscribe({
      next: r => this.avisos.ok(`Enviamos ${r.archivo ?? 'el PDF'} a ${r.para}.`),
      error: e => this.avisos.error(e),
    });
  }

  /** Vuelve a cruzar la factura contra lo recibido hasta hoy. */
  protected recruzar(): void {
    const f = this.factura();
    if (!f?.id) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.recruzarFactura(f.id).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.avisos.ok('Factura cruzada de nuevo con lo recibido.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
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

  /** Vuelve a leer la orden, la factura y los pagos (tras anular un pago o adjuntar un comprobante). */
  protected cargar(): void {
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
