import { LowerCasePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { Bodega, Factura, FacturaNueva, NuevaVersionOrden, Orden, OrdenLinea, PagosOrden, RegistrarPago, ResumenRecepcion } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
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
  private readonly cotizaciones = inject(CotizacionesService);
  private readonly router = inject(Router);
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
  /** Compras puede cancelar o crear una versión nueva mientras la orden no se haya despachado. */
  protected readonly puedeCambiar = computed(() => {
    const e = this.orden()?.estado;
    return this.sesion.tieneRol(['COMPRADOR']) && !this.sesion.soloLectura()
      && (e === 'EMITIDA' || e === 'ACEPTADA_PROVEEDOR' || e === 'EN_PREPARACION' || e === 'RECHAZADA_PROVEEDOR');
  });
  protected readonly panelCambio = signal<'ninguno' | 'cancelar' | 'version'>('ninguno');
  protected readonly motivoCambio = signal('');
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly versionBodega = signal<number | null>(null);
  protected readonly versionLugar = signal('');
  protected readonly versionFecha = signal('');
  protected readonly versionCantidades = signal<Record<number, number>>({});
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

  /** @param panel qué se abre: cancelar la orden o crear una versión nueva */
  protected abrirCambio(panel: 'cancelar' | 'version'): void {
    const o = this.orden();
    if (!o) {
      return;
    }
    this.motivoCambio.set('');
    this.panelCambio.set(panel);
    if (panel === 'version') {
      this.versionBodega.set(o.bodegaDestinoId ?? null);
      this.versionLugar.set(o.lugarEntrega ?? '');
      this.versionFecha.set(o.fechaEntregaEsperada ?? '');
      this.versionCantidades.set(Object.fromEntries((o.lineas ?? []).map(l => [l.id!, l.cantidad ?? 0])));
      if (!this.bodegas().length) {
        this.cotizaciones.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
      }
    }
  }

  protected cerrarCambio(): void {
    this.panelCambio.set('ninguno');
    this.motivoCambio.set('');
  }

  /** @param lineaId línea; @param valor nueva cantidad */
  protected cambiarCantidad(lineaId: number, valor: number): void {
    this.versionCantidades.update(c => ({ ...c, [lineaId]: valor }));
  }

  protected cancelarOrden(): void {
    const o = this.orden();
    const motivo = this.motivoCambio().trim();
    if (!o?.id) {
      return;
    }
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo de la cancelación (mínimo 5 caracteres).');
      return;
    }
    this.trabajando.set(true);
    this.servicio.cancelar(o.id, motivo).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.cerrarCambio();
        this.avisos.ok(`Orden ${r.consecutivo} cancelada. La solicitud vuelve a quedar aprobada y el proveedor recibe aviso.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Manda solo lo que cambió: la orden emitida nunca se edita, se crea una versión nueva con motivo. */
  protected crearVersion(): void {
    const o = this.orden();
    const motivo = this.motivoCambio().trim();
    if (!o?.id) {
      return;
    }
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo de la versión nueva (mínimo 5 caracteres).');
      return;
    }
    const cantidades = (o.lineas ?? []).filter(l => this.versionCantidades()[l.id!] !== l.cantidad)
      .map(l => ({ lineaId: l.id!, cantidad: this.versionCantidades()[l.id!]! }));
    if (cantidades.some(c => !(c.cantidad > 0))) {
      this.avisos.ok('Cada cantidad debe ser mayor que cero.');
      return;
    }
    const datos: NuevaVersionOrden = {
      motivo,
      bodegaDestinoId: this.versionBodega() !== o.bodegaDestinoId ? (this.versionBodega() ?? undefined) : undefined,
      lugarEntrega: this.versionLugar().trim() !== (o.lugarEntrega ?? '') ? this.versionLugar().trim() : undefined,
      fechaEntregaEsperada: this.versionFecha() !== (o.fechaEntregaEsperada ?? '') ? this.versionFecha() : undefined,
      cantidades: cantidades.length ? cantidades : undefined,
    };
    if (!datos.bodegaDestinoId && !datos.lugarEntrega && !datos.fechaEntregaEsperada && !datos.cantidades) {
      this.avisos.ok('No cambiaste nada: modifica la entrega o alguna cantidad.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.nuevaVersion(o.id, datos).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.cerrarCambio();
        this.avisos.ok(`Versión ${r.version} de la orden ${r.consecutivo} emitida. La anterior quedó reemplazada.`);
        void this.router.navigate(['/app/ordenes', r.id]);
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
