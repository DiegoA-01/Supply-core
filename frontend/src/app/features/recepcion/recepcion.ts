import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';

import { AvanceRecepcion, Bodega, LineaRecepcion, MotivoRechazo, Orden, Recepcion, ResumenRecepcion } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/** Lo que la persona digita por línea: cuánto llegó y cuánto rechaza. */
interface Digitado {
  recibido: number | null;
  rechazado: number | null;
  motivo: MotivoRechazo | '';
}

/**
 * Vista 6 · Recibir mercancía con calidad (UC-13). La persona dice cuánto llegó y cuánto rechaza; el backend
 * reparte aceptado, excedente y faltante, sube el stock y deja lo rechazado como devolución pendiente.
 */
@Component({
  selector: 'sc-recepcion',
  imports: [FormsModule, RouterLink, Estado, EtiquetaPipe, NumeroPipe, FechaPipe],
  templateUrl: './recepcion.html',
  styleUrl: './recepcion.scss',
})
export class RecepcionOrden implements OnInit {
  private readonly ordenes = inject(OrdenesService);
  private readonly cotizaciones = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Id de la orden (de la ruta). */
  readonly ordenId = input.required<string>();

  protected readonly motivos: MotivoRechazo[] = ['DANADO', 'NO_CUMPLE_ESPECIFICACION', 'VENCIDO', 'CANTIDAD_ERRADA', 'OTRO'];
  protected readonly orden = signal<Orden | null>(null);
  protected readonly resumen = signal<ResumenRecepcion | null>(null);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly bodegaId = signal(0);
  protected readonly digitado = signal<Record<number, Digitado>>({});
  protected readonly observaciones = signal('');
  protected readonly evidencia = signal<File | null>(null);
  protected readonly registrada = signal<Recepcion | null>(null);
  protected readonly trabajando = signal(false);
  protected readonly cerrando = signal(false);
  protected readonly motivoCierre = signal('');
  /** Se puede cerrar cuando ya entró mercancía (parcial o total); lo cierran bodega y el jefe de compras. */
  protected readonly puedeCerrar = computed(() => this.sesion.tieneRol(['ALMACENISTA', 'ADMIN_COMPRAS']) && !this.sesion.soloLectura()
    && ['RECIBIDA_PARCIAL', 'RECIBIDA_TOTAL'].includes(this.resumen()?.estadoOrden ?? ''));
  /** Cerrar con faltante (recepción parcial) exige un motivo. */
  protected readonly cierraConFaltante = computed(() => this.resumen()?.estadoOrden === 'RECIBIDA_PARCIAL');

  protected readonly puedeRecibir = computed(() => {
    const e = this.resumen()?.estadoOrden;
    return this.sesion.tieneRol(['ALMACENISTA']) && !this.sesion.soloLectura() && (e === 'DESPACHADA' || e === 'RECIBIDA_PARCIAL');
  });
  protected readonly puedeResolver = computed(() => this.sesion.tieneRol(['COMPRADOR', 'ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  /** Excedentes que esperan que compras los acepte o devuelva. */
  protected readonly excedentes = computed(() => (this.resumen()?.recepciones ?? [])
    .flatMap(r => r.lineas ?? []).filter(l => l.estado === 'EN_REVISION'));
  /** Totales de la entrega recién registrada, tal como los repartió el backend. */
  protected readonly totales = computed(() => {
    const l = this.registrada()?.lineas ?? [];
    const suma = (f: (x: LineaRecepcion) => number | undefined) => l.reduce((s, x) => s + (f(x) ?? 0), 0);
    return { aceptada: suma(x => x.cantidadAceptada), rechazada: suma(x => x.cantidadRechazada), excedente: suma(x => x.cantidadExcedente), faltante: suma(x => x.cantidadFaltante) };
  });

  ngOnInit(): void {
    this.cargar();
  }

  /** @param a línea; lo digitado para ella */
  protected valor(a: AvanceRecepcion): Digitado {
    return this.digitado()[a.ordenLineaId!] ?? { recibido: null, rechazado: 0, motivo: '' };
  }

  /** @param a línea; @param campo dato digitado */
  protected cambiar<K extends keyof Digitado>(a: AvanceRecepcion, campo: K, v: Digitado[K]): void {
    this.digitado.update(d => ({ ...d, [a.ordenLineaId!]: { ...this.valor(a), [campo]: v } }));
  }

  /** @param a línea; resultado de la entrega recién registrada */
  protected resultado(a: AvanceRecepcion): LineaRecepcion | undefined {
    return this.registrada()?.lineas?.find(l => l.ordenLineaId === a.ordenLineaId);
  }

  /** @param evento cambio del selector de archivos */
  protected elegirArchivo(evento: Event): void {
    this.evidencia.set((evento.target as HTMLInputElement).files?.[0] ?? null);
  }

  protected registrar(): void {
    const id = Number(this.ordenId());
    const lineas = (this.resumen()?.lineas ?? [])
      .map(a => ({ a, d: this.valor(a) }))
      .filter(({ d }) => (d.recibido ?? 0) > 0)
      .map(({ a, d }) => ({
        ordenLineaId: a.ordenLineaId!,
        cantidadRecibida: d.recibido ?? 0,
        cantidadRechazada: d.rechazado ?? 0,
        motivoRechazo: (d.rechazado ?? 0) > 0 && d.motivo ? d.motivo : undefined,
      }));
    if (!lineas.length) {
      this.avisos.ok('Escribe cuánto llegó de al menos un producto.');
      return;
    }
    if (lineas.some(l => l.cantidadRechazada > 0 && !l.motivoRechazo)) {
      this.avisos.ok('Elige el motivo de cada rechazo.');
      return;
    }
    this.trabajando.set(true);
    this.ordenes.registrarRecepcion(id, { bodegaId: this.bodegaId(), observaciones: this.observaciones().trim(), lineas }).subscribe({
      next: r => {
        this.registrada.set(r);
        const archivo = this.evidencia();
        if (archivo && r.id) {
          this.ordenes.adjuntar('RECEPCION', r.id, archivo).subscribe({ error: e => this.avisos.error(e) });
        }
        this.trabajando.set(false);
        this.digitado.set({});
        this.avisos.ok('Recepción registrada: el stock ya se actualizó.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param l línea con excedente; @param decision aceptar al stock o devolver */
  protected resolver(l: LineaRecepcion, decision: 'ACEPTAR' | 'DEVOLVER'): void {
    const motivo = decision === 'ACEPTAR' ? 'Excedente aceptado al stock' : 'Excedente devuelto al proveedor';
    this.ordenes.resolverExceso(l.id!, decision, motivo).subscribe({
      next: () => {
        this.avisos.ok(decision === 'ACEPTAR' ? 'Excedente sumado al stock.' : 'Excedente marcado para devolver.');
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  protected cerrarRecepcion(): void {
    const o = this.orden();
    const motivo = this.motivoCierre().trim();
    if (!o?.id) {
      return;
    }
    if (this.cierraConFaltante() && motivo.length < 5) {
      this.avisos.ok('Cerrar con faltante exige el motivo (mínimo 5 caracteres).');
      return;
    }
    this.trabajando.set(true);
    this.ordenes.cerrarRecepcion(o.id, motivo).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.cerrando.set(false);
        this.motivoCierre.set('');
        this.avisos.ok('Recepción cerrada. Se evaluó al proveedor y la orden quedó cerrada.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    const id = Number(this.ordenId());
    forkJoin({
      orden: this.ordenes.obtener(id),
      resumen: this.ordenes.recepciones(id),
      bodegas: this.cotizaciones.bodegas().pipe(catchError(() => of([] as Bodega[]))),
    }).subscribe({
      next: r => {
        this.orden.set(r.orden);
        this.resumen.set(r.resumen);
        this.bodegas.set(r.bodegas);
        if (!this.bodegaId()) {
          this.bodegaId.set(r.orden.bodegaDestinoId ?? r.bodegas[0]?.id ?? 0);
        }
      },
      error: e => this.avisos.error(e),
    });
  }
}
