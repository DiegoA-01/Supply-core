import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import {
  CargaPrecios, HistorialPrecio, ListaPrecio, PrecioFila, Precios, TarifaImpuesto, UnidadMedida,
} from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { VentasService } from '../../core/servicios/ventas.service';
import { Avisos, descargar } from '../../core/ui/avisos';

/** Texto de cada alerta de un producto. */
const ALERTAS: Record<string, string> = {
  SIN_PRECIO: 'Sin precio',
  SIN_COSTO: 'Sin costo',
  SIN_IMPUESTO: 'Sin impuesto',
  BAJO_COSTO: 'Bajo el costo',
  MINIMO_BAJO_COSTO: 'Mínimo bajo el costo',
};

/** Precio y datos de venta en edición. */
interface Edicion {
  productoId: number;
  /** Unidad del precio que se edita: null = la del producto (decisión D9: también por caja). */
  unidadId: number | null;
  precio: number | null;
  minimo: number | null;
  motivo: string;
  tarifaId: number | null;
  incluye: boolean;
  vendible: boolean;
  barras: string;
}

/**
 * Precios y margen: el dueño fija el precio de venta de cada producto en cada lista y ve cuánto gana. La base sin
 * impuesto, el margen y el margen global los calcula el backend; esta pantalla solo los muestra y los ordena.
 */
@Component({
  selector: 'sc-precios',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './precios.html',
  styleUrl: './precios.scss',
})
export class PreciosMargen implements OnInit {
  private readonly ventas = inject(VentasService);
  private readonly avisos = inject(Avisos);
  private readonly inventario = inject(InventarioService);

  protected readonly listas = signal<ListaPrecio[]>([]);
  protected readonly impuestos = signal<TarifaImpuesto[]>([]);
  protected readonly listaId = signal<number | null>(null);
  protected readonly datos = signal<Precios | null>(null);
  protected readonly texto = signal('');
  protected readonly filtro = signal<string | null>(null);
  protected readonly edicion = signal<Edicion | null>(null);
  protected readonly historial = signal<{ productoId: number; unidad: string; filas: HistorialPrecio[] } | null>(null);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  /** Archivo de precios elegido y lo que dijo la revisión (todo o nada). */
  protected readonly archivo = signal<File | null>(null);
  protected readonly carga = signal<CargaPrecios | null>(null);
  protected readonly cargando = signal(false);
  protected readonly nuevaLista = signal('');
  protected readonly guardando = signal(false);

  protected readonly filas = computed(() => {
    const t = this.texto().trim().toLowerCase();
    const f = this.filtro();
    return (this.datos()?.filas ?? []).filter(p => (!t || `${p.codigo} ${p.nombre} ${p.categoria}`.toLowerCase().includes(t))
      && (!f || (p.alertas ?? []).includes(f)));
  });

  ngOnInit(): void {
    this.ventas.listas().subscribe({ next: l => this.listas.set(l), error: e => this.avisos.error(e) });
    this.ventas.impuestos().subscribe({ next: l => this.impuestos.set(l.filter(t => t.activo)), error: () => undefined });
    this.inventario.unidades().subscribe({ next: l => this.unidades.set(l), error: () => undefined });
    this.cargar();
  }

  protected cargar(): void {
    this.ventas.precios(this.listaId()).subscribe({
      next: d => {
        this.datos.set(d);
        if (this.listaId() === null && d.lista?.id) {
          this.listaId.set(d.lista.id);
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param id lista elegida */
  protected cambiarLista(id: number): void {
    this.listaId.set(id);
    this.edicion.set(null);
    this.cargar();
  }

  /** @param codigo alerta; texto */
  protected alerta(codigo: string): string {
    return ALERTAS[codigo] ?? codigo;
  }

  /** @param p producto a editar */
  protected editar(p: PrecioFila): void {
    this.historial.set(null);
    this.edicion.set({ productoId: p.productoId!, unidadId: null, precio: p.precio ?? null, minimo: p.precioMinimo ?? null, motivo: '',
      tarifaId: p.tarifaImpuestoId ?? null, incluye: p.precioIncluyeImpuesto ?? true, vendible: p.vendible ?? true, barras: p.codigoBarras ?? '' });
  }

  /**
   * Unidades en las que se puede poner precio a un producto: la suya y las activas de la misma magnitud (se convierten).
   *
   * @param p producto
   */
  protected unidadesDe(p: PrecioFila): UnidadMedida[] {
    const propia = this.unidades().find(u => u.codigo === p.unidad);
    return propia ? this.unidades().filter(u => u.id !== propia.id && u.magnitud === propia.magnitud) : [];
  }

  /**
   * Cambia la unidad del precio que se edita y trae el precio vigente en esa unidad, si tiene.
   *
   * @param p        producto
   * @param unidadId unidad (null = la del producto)
   */
  protected cambiarUnidad(p: PrecioFila, unidadId: number | null): void {
    const otra = unidadId === null ? null : (p.otrasUnidades ?? []).find(o => o.unidadMedidaId === unidadId);
    this.edicion.update(e => (e ? {
      ...e, unidadId,
      precio: unidadId === null ? p.precio ?? null : otra?.precio ?? null,
      minimo: unidadId === null ? p.precioMinimo ?? null : otra?.precioMinimo ?? null,
    } : e));
  }

  /** @param campo dato; @param valor valor escrito */
  protected cambiar<K extends keyof Edicion>(campo: K, valor: Edicion[K]): void {
    this.edicion.update(e => (e ? { ...e, [campo]: valor } : e));
  }

  /** @param p producto; guarda primero los datos de venta si cambiaron y luego el precio */
  protected guardar(p: PrecioFila): void {
    const e = this.edicion();
    const lista = this.listaId();
    if (!e || lista === null) {
      return;
    }
    if (e.precio === null || !(e.precio > 0)) {
      this.avisos.ok('Escribe un precio mayor que cero.');
      return;
    }
    if (e.minimo !== null && (e.minimo <= 0 || e.minimo > e.precio)) {
      this.avisos.ok('El precio mínimo debe ser mayor que cero y no superar el precio.');
      return;
    }
    const cambiaVenta = e.tarifaId !== (p.tarifaImpuestoId ?? null) || e.incluye !== p.precioIncluyeImpuesto
      || e.vendible !== p.vendible || (e.barras.trim() || null) !== (p.codigoBarras ?? null);
    const otra = e.unidadId === null ? null : (p.otrasUnidades ?? []).find(o => o.unidadMedidaId === e.unidadId);
    const actual = e.unidadId === null ? { precio: p.precio, minimo: p.precioMinimo } : { precio: otra?.precio, minimo: otra?.precioMinimo };
    const cambiaPrecio = e.precio !== (actual.precio ?? null) || e.minimo !== (actual.minimo ?? null);
    this.guardando.set(true);
    const terminar = () => {
      this.guardando.set(false);
      this.edicion.set(null);
      this.avisos.ok('Guardado. El margen se recalculó.');
      this.cargar();
    };
    const fallo = (err: unknown) => {
      this.guardando.set(false);
      this.avisos.error(err);
    };
    const fijar = () => cambiaPrecio
      ? this.ventas.fijarPrecio(lista, e.productoId, e.precio!, e.minimo, e.motivo.trim() || null, e.unidadId)
        .subscribe({ next: terminar, error: fallo })
      : terminar();
    if (cambiaVenta) {
      this.ventas.configurarProducto(e.productoId, e.tarifaId, e.vendible, e.incluye, e.barras.trim() || null)
        .subscribe({ next: () => fijar(), error: fallo });
    } else {
      fijar();
    }
  }

  /**
   * Muestra u oculta el historial de precios del producto en la lista, en su unidad o en otra.
   *
   * @param p        producto
   * @param unidadId unidad (null = la del producto)
   * @param unidad   código de la unidad para el título
   */
  protected verHistorial(p: PrecioFila, unidadId: number | null = null, unidad = p.unidad ?? ''): void {
    const lista = this.listaId();
    if (lista === null) {
      return;
    }
    const h = this.historial();
    if (h && h.productoId === p.productoId && h.unidad === unidad) {
      this.historial.set(null);
      return;
    }
    this.edicion.set(null);
    this.ventas.historial(lista, p.productoId!, unidadId).subscribe({
      next: filas => this.historial.set({ productoId: p.productoId!, unidad, filas }),
      error: e => this.avisos.error(e),
    });
  }

  /** @param evento cambio del campo de archivo; guarda el Excel elegido y lo revisa sin guardar */
  protected elegirArchivo(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const f = entrada.files?.[0] ?? null;
    entrada.value = '';
    this.archivo.set(f);
    this.carga.set(null);
    if (f) {
      this.subir(true);
    }
  }

  /** @param soloValidar true para revisar; false para fijar los precios (todo o nada) */
  protected subir(soloValidar: boolean): void {
    const f = this.archivo();
    if (!f) {
      return;
    }
    this.cargando.set(true);
    this.ventas.cargarPrecios(f, soloValidar).subscribe({
      next: r => {
        this.cargando.set(false);
        this.carga.set(r);
        if (r.aplicado) {
          this.avisos.ok(`${r.precios} precios fijados. El anterior de cada uno quedó en el historial.`);
          this.archivo.set(null);
          this.cargar();
        }
      },
      error: e => {
        this.cargando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected bajarPlantilla(): void {
    this.ventas.plantillaPrecios().subscribe({
      next: b => descargar(b, 'plantilla-precios.xlsx'),
      error: e => this.avisos.error(e),
    });
  }

  protected crearLista(): void {
    const nombre = this.nuevaLista().trim();
    if (!nombre) {
      return;
    }
    this.ventas.crearLista(nombre).subscribe({
      next: l => {
        this.nuevaLista.set('');
        this.listas.update(ls => [...ls, l]);
        this.avisos.ok(`Lista ${l.nombre} creada.`);
        this.cambiarLista(l.id!);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param pct margen en %; tono del chip */
  protected tono(pct: number | null | undefined): string {
    if (pct == null) {
      return 'gris';
    }
    return pct < 0 ? 'rojo' : pct < 15 ? 'ambar' : 'verde';
  }
}
