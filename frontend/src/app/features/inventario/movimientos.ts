import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Bodega, Movimiento, MovimientoNuevo, Pagina, Producto, RegistroMovimiento, StockBodega, TipoMovimiento } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { FiltroMovimientos, InventarioService } from '../../core/servicios/inventario.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { BuscadorProducto } from '../../core/ui/buscador-producto';
import { Avisos, descargar } from '../../core/ui/avisos';

/** Tipos que el almacenista puede registrar a mano (el resto los generan recepción y despachos). */
const MANUALES: readonly TipoMovimiento[] = ['SALIDA_CONSUMO', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'TRASLADO'];
const USAN_ORIGEN: readonly TipoMovimiento[] = ['SALIDA_CONSUMO', 'AJUSTE_NEGATIVO', 'TRASLADO'];
const USAN_DESTINO: readonly TipoMovimiento[] = ['AJUSTE_POSITIVO', 'TRASLADO'];
const EXIGEN_MOTIVO: readonly TipoMovimiento[] = ['AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO'];
const TODOS_LOS_TIPOS: readonly TipoMovimiento[] = [
  'ENTRADA_COMPRA', 'SALIDA_CONSUMO', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO', 'DEVOLUCION_PROVEEDOR', 'TRASLADO', 'SALIDA_VENTA', 'ANULACION_DESPACHO',
  'ANULACION_VENTA', 'DEVOLUCION_CLIENTE',
];

/**
 * Movimientos de stock: existencias por bodega, kardex con filtros y exportación, y registro de salidas, ajustes y
 * traslados (almacenista). El stock solo cambia por movimientos; esta pantalla nunca calcula cantidades.
 */
@Component({
  selector: 'sc-movimientos',
  imports: [FormsModule, RouterLink, Estado, EtiquetaPipe, FechaPipe, NumeroPipe],
  templateUrl: './movimientos.html',
  styleUrl: './movimientos.scss',
})
export class Movimientos implements OnInit {
  private readonly servicio = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly vista = signal<'stock' | 'kardex'>('stock');
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly tiposFiltro = TODOS_LOS_TIPOS;
  protected readonly tiposManuales = MANUALES;
  protected readonly trabajando = signal(false);

  // Existencias
  protected readonly existencias = signal<StockBodega[] | null>(null);
  protected readonly bodegaStock = signal<number | null>(null);
  protected readonly textoStock = signal('');
  protected readonly filas = computed(() => {
    const t = this.textoStock().trim().toLowerCase();
    return (this.existencias() ?? []).filter(s => !t || `${s.productoCodigo} ${s.productoNombre}`.toLowerCase().includes(t));
  });

  // Kardex
  protected readonly filtroProducto = new BuscadorProducto(this.servicio);
  protected readonly bodegaKardex = signal<number | null>(null);
  protected readonly tipoKardex = signal<string | null>(null);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly pagina = signal<Pagina<Movimiento> | null>(null);
  protected readonly numeroPagina = signal(0);

  // Registro
  protected readonly registrando = signal(false);
  protected readonly formProducto = new BuscadorProducto(this.servicio);
  protected readonly tipo = signal<TipoMovimiento>('SALIDA_CONSUMO');
  protected readonly origen = signal<number | null>(null);
  protected readonly destino = signal<number | null>(null);
  protected readonly cantidad = signal<number | null>(null);
  protected readonly motivo = signal('');
  protected readonly resultado = signal<RegistroMovimiento | null>(null);

  protected readonly puedeRegistrar = computed(() => this.sesion.tieneRol(['ALMACENISTA']) && !this.sesion.soloLectura());
  protected readonly usaOrigen = computed(() => USAN_ORIGEN.includes(this.tipo()));
  protected readonly usaDestino = computed(() => USAN_DESTINO.includes(this.tipo()));
  protected readonly exigeMotivo = computed(() => EXIGEN_MOTIVO.includes(this.tipo()));

  ngOnInit(): void {
    this.servicio.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    this.cargarStock();
  }

  /** @param v pestaña que se abre */
  protected abrirVista(v: 'stock' | 'kardex'): void {
    this.vista.set(v);
    if (v === 'kardex' && !this.pagina()) {
      this.buscar(0);
    }
  }

  /** @param id bodega ({@code null} = todas) */
  protected filtrarBodegaStock(id: number | null): void {
    this.bodegaStock.set(id);
    this.cargarStock();
  }

  /** @param p número de página desde 0 */
  protected buscar(p: number): void {
    this.pagina.set(null);
    this.numeroPagina.set(p);
    this.servicio.movimientos(this.filtros(), p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 25, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  protected limpiarFiltros(): void {
    this.filtroProducto.limpiar();
    this.bodegaKardex.set(null);
    this.tipoKardex.set(null);
    this.desde.set('');
    this.hasta.set('');
    this.buscar(0);
  }

  protected exportarExcel(): void {
    if (!this.filtroProducto.id()) {
      this.avisos.ok('Elige un producto de la lista para exportar su kardex.');
      return;
    }
    this.servicio.kardexExcel(this.filtros()).subscribe({
      next: b => descargar(b, 'kardex.xlsx'),
      error: e => this.avisos.error(e),
    });
  }

  protected exportarPorCorreo(): void {
    if (!this.filtroProducto.id()) {
      this.avisos.ok('Elige un producto de la lista para exportar su kardex.');
      return;
    }
    this.servicio.kardexPorCorreo(this.filtros()).subscribe({
      next: r => this.avisos.ok(`Enviamos ${r.archivo ?? 'el Excel'} a ${r.para}.`),
      error: e => this.avisos.error(e),
    });
  }

  protected abrirRegistro(): void {
    this.registrando.set(true);
    this.resultado.set(null);
    this.tipo.set('SALIDA_CONSUMO');
    this.formProducto.limpiar();
    this.origen.set(null);
    this.destino.set(null);
    this.cantidad.set(null);
    this.motivo.set('');
  }

  /** @param t tipo elegido; al cambiar de tipo se limpian las bodegas que ya no aplican */
  protected cambiarTipo(t: TipoMovimiento): void {
    this.tipo.set(t);
    if (!USAN_ORIGEN.includes(t)) {
      this.origen.set(null);
    }
    if (!USAN_DESTINO.includes(t)) {
      this.destino.set(null);
    }
  }

  protected registrar(): void {
    const error = this.validar();
    if (error) {
      this.avisos.ok(error);
      return;
    }
    const datos: MovimientoNuevo = {
      productoId: this.formProducto.id()!,
      tipo: this.tipo(),
      bodegaOrigenId: this.usaOrigen() ? this.origen()! : undefined,
      bodegaDestinoId: this.usaDestino() ? this.destino()! : undefined,
      cantidad: this.cantidad()!,
      motivo: this.motivo().trim() || undefined,
    };
    this.trabajando.set(true);
    this.servicio.registrarMovimiento(datos).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.resultado.set(r);
        this.registrando.set(false);
        this.avisos.ok('Movimiento registrado.');
        this.cargarStock();
        if (this.pagina()) {
          this.buscar(0);
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Revisa lo mínimo antes de enviar; el backend vuelve a validar todo (existencias, bodegas activas, motivo). */
  private validar(): string | null {
    if (!this.formProducto.id()) {
      return 'Elige un producto de la lista.';
    }
    if (this.usaOrigen() && !this.origen()) {
      return 'Elige la bodega de origen.';
    }
    if (this.usaDestino() && !this.destino()) {
      return 'Elige la bodega de destino.';
    }
    if (this.tipo() === 'TRASLADO' && this.origen() === this.destino()) {
      return 'El traslado necesita dos bodegas distintas.';
    }
    if (!this.cantidad() || this.cantidad()! <= 0) {
      return 'Escribe una cantidad mayor que cero.';
    }
    if (this.exigeMotivo() && this.motivo().trim().length < 5) {
      return 'El motivo del ajuste es obligatorio (mínimo 5 caracteres).';
    }
    return null;
  }

  private filtros(): FiltroMovimientos {
    return {
      productoId: this.filtroProducto.id(),
      bodegaId: this.bodegaKardex(),
      tipo: this.tipoKardex(),
      desde: this.desde() ? `${this.desde()}T00:00:00` : null,
      hasta: this.hasta() ? `${this.hasta()}T23:59:59` : null,
    };
  }

  private cargarStock(): void {
    this.existencias.set(null);
    this.servicio.stock(this.bodegaStock()).subscribe({
      next: l => this.existencias.set(l),
      error: e => {
        this.existencias.set([]);
        this.avisos.error(e);
      },
    });
  }
}
