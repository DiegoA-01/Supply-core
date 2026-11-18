import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { Extraccion } from './extraccion';
import { Adjudicacion, Analisis, Bodega, Cotizacion, Pesos, ResultadoAnalisis, Rfq } from '../../core/api/modelos';
import { Estado, EtiquetaPipe, etiqueta } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe, PuntajePipe } from '../../core/formato/formato';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/**
 * Vista 4 · Análisis de cotizaciones (UC-08 y UC-09). Java calcula puntajes y totales; la IA solo explica.
 * Apartarse de la recomendación exige justificación (lo valida también el backend).
 */
@Component({
  selector: 'sc-analisis',
  imports: [FormsModule, RouterLink, Extraccion, Estado, EtiquetaPipe, PesosPipe, PuntajePipe, NumeroPipe, FechaPipe],
  templateUrl: './analisis.html',
  styleUrl: './analisis.scss',
})
export class AnalisisRfq implements OnInit {
  private readonly servicio = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);
  protected readonly sesion = inject(SesionService);

  /** Id de la RFQ (de la ruta). */
  readonly rfqId = input.required<string>();

  protected readonly rfq = signal<Rfq | null>(null);
  protected readonly analisis = signal<Analisis | null>(null);
  protected readonly sinAnalisis = signal(false);
  protected readonly cotizaciones = signal<Cotizacion[]>([]);
  protected readonly pesos = signal<Pesos | null>(null);
  protected readonly adjudicacion = signal<Adjudicacion | null>(null);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly trabajando = signal(false);

  protected readonly elegida = signal<number | null>(null);
  protected readonly justificacion = signal('');
  protected readonly orden = signal({ bodegaDestinoId: 0, lugarEntrega: '', fechaEntregaEsperada: '' });

  protected readonly esComprador = computed(() => this.sesion.tieneRol(['COMPRADOR']) && !this.sesion.soloLectura());
  protected readonly puedeCancelarRfq = computed(() => this.esComprador() && ['BORRADOR', 'ABIERTA'].includes(this.rfq()?.estado ?? ''));
  protected readonly leidasPorIa = computed(() => this.cotizaciones().filter(c => c.origen === 'ARCHIVO'));
  protected readonly puedeVerLectura = computed(() => this.sesion.tieneRol(['COMPRADOR', 'ADMIN_COMPRAS', 'AUDITOR']));
  protected readonly puedeEditarLectura = computed(() => this.sesion.tieneRol(['COMPRADOR', 'ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly resultados = computed(() => [...(this.analisis()?.resultados ?? [])].sort((a, b) => (a.posicion ?? 99) - (b.posicion ?? 99)));
  protected readonly recomendada = computed(() => this.resultados()[0]?.cotizacionId ?? null);
  protected readonly criterios = computed(() => this.resultados()[0]?.criterios ?? []);
  protected readonly alertas = computed(() => this.resultados().flatMap(r => (r.anomalias ?? []).map(a => ({ ...a, proveedor: r.proveedor }))));
  protected readonly sigueRecomendacion = computed(() => this.elegida() === this.recomendada());
  protected readonly publicado = computed(() => this.analisis()?.estado === 'PUBLICADO');

  ngOnInit(): void {
    this.cargar();
    this.servicio.pesos().subscribe({ next: p => this.pesos.set(p), error: () => undefined });
  }

  /** @param r resultado; cotización con plazo, pago y origen */
  protected cotizacion(r: ResultadoAnalisis): Cotizacion | undefined {
    return this.cotizaciones().find(c => c.id === r.cotizacionId);
  }

  /** @param codigo código del criterio; nombre y peso de la configuración usada */
  protected nombreCriterio(codigo: string | undefined): string {
    return this.pesos()?.pesos?.find(p => p.codigo === codigo)?.nombre ?? etiqueta(codigo);
  }

  /** @param r resultado; puntaje del criterio indicado */
  protected ponderado(r: ResultadoAnalisis, codigo: string | undefined): number | undefined {
    return r.criterios?.find(c => c.codigo === codigo)?.ponderado;
  }

  /** @param accion abrir (envía las invitaciones) o cerrar antes de la fecha límite */
  protected cambiarRfq(accion: 'abrir' | 'cerrar'): void {
    if (accion === 'cerrar' && !window.confirm('¿Cerrar la RFQ ahora? Los proveedores ya no podrán cotizar.')) {
      return;
    }
    this.trabajando.set(true);
    (accion === 'abrir' ? this.servicio.abrirRfq(this.id()) : this.servicio.cerrarRfq(this.id())).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.rfq.set(r);
        this.avisos.ok(accion === 'abrir' ? `Invitaciones enviadas a ${r.invitados?.length ?? 0} proveedores.` : 'RFQ cerrada: ya puedes calcular el análisis.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Cancela la RFQ (en borrador o abierta); la solicitud vuelve a quedar aprobada. */
  protected cancelarRfq(): void {
    if (!window.confirm('¿Cancelar esta RFQ? La solicitud vuelve a quedar aprobada y los proveedores invitados dejan de poder cotizar.')) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.cancelarRfq(this.id()).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.rfq.set(r);
        this.avisos.ok('RFQ cancelada. La solicitud quedó aprobada para cotizarla de nuevo.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected generar(): void {
    this.ejecutar(this.servicio.generarAnalisis(this.id()), 'Análisis calculado.');
  }

  protected explicar(): void {
    this.ejecutar(this.servicio.explicar(this.id()), 'La IA agregó la explicación.');
  }

  protected publicar(): void {
    this.ejecutar(this.servicio.publicar(this.id()), 'Análisis publicado.');
  }

  protected adjudicar(): void {
    const cotizacionId = this.elegida();
    if (!cotizacionId) {
      return;
    }
    if (!this.sigueRecomendacion() && !this.justificacion().trim()) {
      this.avisos.ok('Escribe la justificación: es obligatoria si no eliges la recomendada.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.adjudicar(this.id(), cotizacionId, this.justificacion().trim()).subscribe({
      next: a => {
        this.trabajando.set(false);
        this.adjudicacion.set(a);
        this.avisos.ok(a.estado === 'APROBADA' ? 'Adjudicada. Ya puedes emitir la orden.' : 'Adjudicada: espera la aprobación por el monto.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected emitir(): void {
    const a = this.adjudicacion();
    const o = this.orden();
    if (!a?.id || !o.bodegaDestinoId || !o.lugarEntrega.trim() || !o.fechaEntregaEsperada) {
      this.avisos.ok('Completa bodega, lugar y fecha de entrega.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.emitirOrden({ adjudicacionId: a.id, ...o, lugarEntrega: o.lugarEntrega.trim() }).subscribe({
      next: orden => {
        this.avisos.ok(`Orden ${orden.consecutivo} emitida y enviada al proveedor.`);
        void this.router.navigate(['/app/ordenes', orden.id]);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param campo campo de la orden por emitir */
  protected cambiarOrden(campo: 'bodegaDestinoId' | 'lugarEntrega' | 'fechaEntregaEsperada', valor: string | number): void {
    this.orden.update(o => ({ ...o, [campo]: valor }));
  }

  private id(): number {
    return Number(this.rfqId());
  }

  /** Vuelve a leer RFQ, cotizaciones, análisis y adjudicación (tras corregir una lectura con IA). */
  protected cargar(): void {
    const id = this.id();
    forkJoin({
      rfq: this.servicio.rfq(id),
      cotizaciones: this.servicio.cotizaciones(id).pipe(catchError(() => of([] as Cotizacion[]))),
      analisis: this.servicio.analisis(id).pipe(catchError((e: HttpErrorResponse) => e.status === 404 ? of(null) : of(null))),
      adjudicacion: this.servicio.adjudicacion(id).pipe(catchError(() => of(null))),
    }).subscribe({
      next: r => {
        this.rfq.set(r.rfq);
        this.cotizaciones.set(r.cotizaciones);
        this.analisis.set(r.analisis);
        this.sinAnalisis.set(r.analisis === null);
        this.adjudicacion.set(r.adjudicacion);
        this.elegida.set(this.recomendada());
        if (r.adjudicacion?.estado === 'APROBADA') {
          this.cargarBodegas();
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargarBodegas(): void {
    const manana = new Date();
    manana.setDate(manana.getDate() + 3);
    this.servicio.bodegas().subscribe({
      next: l => {
        this.bodegas.set(l);
        const b = l[0];
        this.orden.set({ bodegaDestinoId: b?.id ?? 0, lugarEntrega: b?.nombre ?? '', fechaEntregaEsperada: iso(manana) });
      },
      error: () => undefined,
    });
  }

  private ejecutar(peticion: ReturnType<CotizacionesService['generarAnalisis']>, mensaje: string): void {
    this.trabajando.set(true);
    peticion.subscribe({
      next: a => {
        this.trabajando.set(false);
        this.analisis.set(a);
        this.sinAnalisis.set(false);
        this.elegida.set(this.recomendada());
        this.avisos.ok(mensaje);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
