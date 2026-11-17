import { Component, DestroyRef, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { CampoLeido, Cotizacion, LineaDigitada, Procesamiento } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { Avisos } from '../../core/ui/avisos';

/** Nombre legible de cada dato que lee la IA. */
const NOMBRES: Record<string, string> = {
  cantidad: 'Cantidad',
  unidad: 'Unidad',
  factorConversion: 'Factor de conversión',
  precioUnitario: 'Precio unitario',
  descuentoPct: 'Descuento %',
  impuestoPct: 'IVA %',
  plazoEntregaDias: 'Plazo de entrega (días)',
  garantiaMeses: 'Garantía (meses)',
  validezHasta: 'Válida hasta',
  moneda: 'Moneda',
  totalImpreso: 'Total impreso en el documento',
  renglonNoLeido: 'Renglón que no se pudo leer',
};

/** Fila editable de la digitación a mano. */
interface FilaDigitada extends LineaDigitada {
  producto: string;
}

/**
 * Revisión de la lectura con IA de una cotización recibida como archivo: estado, alertas (por ejemplo, el total impreso
 * que no cuadra), datos leídos con su confianza, confirmación o corrección, nueva lectura y digitación a mano.
 * La IA solo copia lo que ve; los montos los calcula el backend y esta pantalla nunca los calcula.
 */
@Component({
  selector: 'sc-extraccion',
  imports: [FormsModule, Estado],
  templateUrl: './extraccion.html',
  styleUrl: './extraccion.scss',
})
export class Extraccion implements OnInit {
  private readonly servicio = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);
  private readonly destruir = inject(DestroyRef);

  /** Cotización recibida como archivo. */
  readonly cotizacion = input.required<Cotizacion>();
  /** Si el usuario puede confirmar, corregir, reintentar o digitar (comprador). */
  readonly puedeEditar = input(false);
  /** Avisa que cambió algo que afecta al análisis (para que la pantalla lo recargue). */
  readonly cambio = output<void>();

  protected readonly proceso = signal<Procesamiento | null>(null);
  protected readonly sinLectura = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly soloPendientes = signal(true);
  protected readonly borradores = signal<Record<number, string>>({});
  protected readonly digitando = signal(false);
  protected readonly filas = signal<FilaDigitada[]>([]);

  /** Alertas con el código separado del texto: "TOTAL_NO_CUADRA: el total…". */
  protected readonly alertas = computed(() => (this.proceso()?.alertas ?? []).map(a => {
    const i = a.indexOf(':');
    return i > 0 ? { codigo: a.slice(0, i).trim(), texto: a.slice(i + 1).trim() } : { codigo: '', texto: a };
  }));
  protected readonly campos = computed(() => this.proceso()?.campos ?? []);
  protected readonly pendientes = computed(() => this.campos().filter(c => c.pendiente));
  protected readonly visibles = computed(() => (this.soloPendientes() && this.pendientes().length ? this.pendientes() : this.campos()));
  protected readonly total = computed(() => this.campos().find(c => c.campo === 'totalImpreso'));
  protected readonly enCurso = computed(() => ['PENDIENTE', 'PROCESANDO'].includes(this.proceso()?.estado ?? ''));
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    this.destruir.onDestroy(() => this.detener());
  }

  ngOnInit(): void {
    this.cargar();
  }

  /** @param p procesamiento; umbral de confianza como porcentaje entero */
  protected umbral(p: Procesamiento): number {
    return Math.round((p.umbralConfianza ?? 0) * 100);
  }

  /** @param c dato leído; nombre para mostrar */
  protected nombre(c: CampoLeido): string {
    return NOMBRES[c.campo ?? ''] ?? c.campo ?? '';
  }

  /** @param c dato leído; producto al que pertenece la línea, o "Cabecera" */
  protected linea(c: CampoLeido): string {
    if (!c.cotizacionLineaId) {
      return 'Documento';
    }
    return this.cotizacion().lineas?.find(l => l.id === c.cotizacionLineaId)?.productoCodigo ?? `Línea ${c.cotizacionLineaId}`;
  }

  /** @param c dato leído; confianza como porcentaje entero */
  protected confianza(c: CampoLeido): string {
    return c.confianza == null ? '—' : `${Math.round(c.confianza * 100)} %`;
  }

  /** @param c dato leído; valor que se ve en la caja de edición */
  protected borrador(c: CampoLeido): string {
    return this.borradores()[c.id!] ?? c.valorConfirmado ?? c.valorExtraido ?? '';
  }

  /** @param c dato leído; @param valor texto escrito */
  protected escribir(c: CampoLeido, valor: string): void {
    this.borradores.update(b => ({ ...b, [c.id!]: valor }));
  }

  /** Confirma el dato tal como está en la caja (si se corrigió, el backend guarda la corrección). */
  protected confirmar(c: CampoLeido): void {
    const valor = this.borrador(c).trim();
    if (!valor) {
      this.avisos.ok('Escribe el valor correcto antes de confirmar.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.confirmarCampo(this.cotizacion().id!, c.id!, valor).subscribe({
      next: p => this.recibido(p, 'Dato confirmado.'),
      error: e => this.fallo(e),
    });
  }

  protected reintentar(): void {
    if (!window.confirm('Se vuelve a leer el archivo con la IA y se pierden las correcciones hechas a mano. ¿Continuar?')) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.reintentarExtraccion(this.cotizacion().id!).subscribe({
      next: p => this.recibido(p, 'La IA está leyendo el archivo de nuevo.'),
      error: e => this.fallo(e),
    });
  }

  /** Abre o cierra la digitación a mano, con los valores actuales de cada línea. */
  protected alternarDigitacion(): void {
    if (!this.digitando()) {
      this.filas.set((this.cotizacion().lineas ?? []).map(l => ({
        producto: l.productoCodigo ?? `Línea ${l.rfqLineaId}`,
        rfqLineaId: l.rfqLineaId!,
        cantidadOfertada: l.cantidadOfertada ?? 0,
        unidadOfertada: l.unidadOfertada ?? '',
        factorConversion: l.factorConversion ?? 1,
        precioUnitarioOfertado: l.precioUnitarioOfertado ?? 0,
        descuentoPct: l.descuentoPct ?? 0,
        impuestoPct: l.impuestoPct ?? 0,
      })));
    }
    this.digitando.update(v => !v);
  }

  /** @param i fila; @param campo columna; @param valor texto escrito */
  protected cambiarFila(i: number, campo: keyof LineaDigitada, valor: string | number): void {
    this.filas.update(f => f.map((x, k) => (k === i ? { ...x, [campo]: valor } : x)));
  }

  protected guardarDigitacion(): void {
    const lineas = this.filas();
    if (!lineas.length || lineas.some(l => !(l.cantidadOfertada > 0) || !(l.precioUnitarioOfertado > 0) || !(l.factorConversion > 0) || !l.unidadOfertada.trim())) {
      this.avisos.ok('Cada línea necesita cantidad, unidad, factor y precio mayores que cero.');
      return;
    }
    this.trabajando.set(true);
    const cuerpo: LineaDigitada[] = lineas.map(({ producto: _p, ...l }) => ({ ...l, unidadOfertada: l.unidadOfertada.trim() }));
    this.servicio.digitarLineas(this.cotizacion().id!, cuerpo).subscribe({
      next: p => {
        this.digitando.set(false);
        this.recibido(p, 'Precios digitados guardados.');
      },
      error: e => this.fallo(e),
    });
  }

  private recibido(p: Procesamiento, mensaje: string): void {
    this.trabajando.set(false);
    this.proceso.set(p);
    this.borradores.set({});
    this.avisos.ok(mensaje);
    this.cambio.emit();
    this.sondear();
  }

  private fallo(e: unknown): void {
    this.trabajando.set(false);
    this.avisos.error(e);
  }

  private cargar(): void {
    this.servicio.extraccion(this.cotizacion().id!).subscribe({
      next: p => {
        this.proceso.set(p);
        this.sondear();
      },
      error: () => this.sinLectura.set(true),
    });
  }

  /** Mientras la IA lee, consulta cada 3 segundos hasta que termine. */
  private sondear(): void {
    this.detener();
    if (!this.enCurso()) {
      return;
    }
    this.temporizador = setTimeout(() => this.servicio.extraccion(this.cotizacion().id!).subscribe({
      next: p => {
        const seguia = this.enCurso();
        this.proceso.set(p);
        if (seguia && !this.enCurso()) {
          this.cambio.emit();
        }
        this.sondear();
      },
      error: () => undefined,
    }), 3000);
  }

  private detener(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
  }
}
