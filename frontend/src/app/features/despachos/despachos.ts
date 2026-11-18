import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Bodega, Despacho, DespachoNuevo, DespachoResumen, Pagina, UnidadMedida } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { DespachosService } from '../../core/servicios/despachos.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { BuscadorProducto } from '../../core/ui/buscador-producto';

/** Línea del formulario: un producto, su unidad y la cantidad. */
interface LineaForm {
  buscador: BuscadorProducto;
  unidadId: number | null;
  cantidad: number | null;
}

/**
 * Despachos: salidas de mercancía (a un cliente o por consumo). Nacen en borrador, no tocan el stock; al confirmarlos
 * se descuenta; al anular uno confirmado, el stock vuelve. El almacenista arma y confirma; compras puede anular.
 * Las cantidades en la unidad del producto las calcula el backend.
 */
@Component({
  selector: 'sc-despachos',
  imports: [FormsModule, RouterLink, Estado, FechaPipe, NumeroPipe],
  templateUrl: './despachos.html',
  styleUrl: './despachos.scss',
})
export class Despachos implements OnInit {
  private readonly servicio = inject(DespachosService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pagina = signal<Pagina<DespachoResumen> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  protected readonly detalle = signal<Despacho | null>(null);
  protected readonly trabajando = signal(false);

  // Filtros
  protected readonly texto = signal('');
  protected readonly estado = signal<string | null>(null);
  protected readonly bodegaFiltro = signal<number | null>(null);

  // Formulario
  protected readonly formulario = signal<'cerrado' | 'nuevo' | 'editar'>('cerrado');
  protected readonly bodegaId = signal<number | null>(null);
  protected readonly cliente = signal('');
  protected readonly referencia = signal('');
  protected readonly observaciones = signal('');
  protected readonly lineas = signal<LineaForm[]>([]);

  // Anulación
  protected readonly anulando = signal(false);
  protected readonly motivo = signal('');

  protected readonly puedeArmar = computed(() => this.sesion.tieneRol(['ALMACENISTA']) && !this.sesion.soloLectura());
  protected readonly puedeAnular = computed(() => this.sesion.tieneRol(['ALMACENISTA', 'ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly enBorrador = computed(() => this.detalle()?.estado === 'BORRADOR');

  ngOnInit(): void {
    this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    this.inventario.unidades().subscribe({ next: l => this.unidades.set(l), error: () => undefined });
    this.buscar(0);
  }

  /** @param p número de página desde 0 */
  protected buscar(p: number): void {
    this.pagina.set(null);
    this.numeroPagina.set(p);
    this.servicio.buscar({ texto: this.texto(), estado: this.estado(), bodegaId: this.bodegaFiltro() }, p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  /** @param d despacho de la lista; abre su detalle */
  protected abrir(d: DespachoResumen): void {
    this.formulario.set('cerrado');
    this.cancelarAnulacion();
    this.servicio.obtener(d.id!).subscribe({ next: x => this.detalle.set(x), error: e => this.avisos.error(e) });
  }

  protected nuevo(): void {
    this.detalle.set(null);
    this.formulario.set('nuevo');
    this.bodegaId.set(this.bodegas()[0]?.id ?? null);
    this.cliente.set('');
    this.referencia.set('');
    this.observaciones.set('');
    this.lineas.set([this.lineaVacia()]);
  }

  /** Carga el borrador abierto en el formulario para corregirlo. */
  protected editar(): void {
    const d = this.detalle();
    if (!d) {
      return;
    }
    this.formulario.set('editar');
    this.bodegaId.set(d.bodegaId ?? null);
    this.cliente.set(d.cliente ?? '');
    this.referencia.set(d.referenciaExterna ?? '');
    this.observaciones.set(d.observaciones ?? '');
    this.lineas.set((d.lineas ?? []).map(l => {
      const b = new BuscadorProducto(this.inventario);
      b.elegir({ id: l.productoId, codigo: l.productoCodigo, nombre: l.productoNombre });
      return { buscador: b, unidadId: l.unidadMedidaId ?? null, cantidad: l.cantidad ?? null };
    }));
  }

  protected agregarLinea(): void {
    this.lineas.update(l => [...l, this.lineaVacia()]);
  }

  /** @param i posición de la línea que se quita */
  protected quitarLinea(i: number): void {
    this.lineas.update(l => (l.length > 1 ? l.filter((_, k) => k !== i) : l));
  }

  /** @param i línea; @param campo columna; @param valor lo escrito */
  protected cambiarLinea(i: number, campo: 'unidadId' | 'cantidad', valor: number | null): void {
    this.lineas.update(l => l.map((x, k) => (k === i ? { ...x, [campo]: valor } : x)));
  }

  /** @param l línea; unidad que se usa: la elegida o, si no, la del producto */
  protected unidadDe(l: LineaForm): number | null {
    return l.unidadId ?? l.buscador.producto()?.unidadMedida?.id ?? null;
  }

  protected guardar(): void {
    const error = this.validar();
    if (error) {
      this.avisos.ok(error);
      return;
    }
    const datos: DespachoNuevo = {
      bodegaId: this.bodegaId()!,
      cliente: this.cliente().trim() || undefined,
      referenciaExterna: this.referencia().trim() || undefined,
      observaciones: this.observaciones().trim() || undefined,
      lineas: this.lineas().map(l => ({ productoId: l.buscador.id()!, unidadMedidaId: this.unidadDe(l)!, cantidad: l.cantidad! })),
    };
    const editando = this.formulario() === 'editar' ? this.detalle() : null;
    this.trabajando.set(true);
    (editando?.id ? this.servicio.actualizar(editando.id, datos) : this.servicio.crear(datos)).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.formulario.set('cerrado');
        this.detalle.set(d);
        this.avisos.ok(`Despacho ${d.consecutivo} guardado en borrador. El stock no cambia hasta que lo confirmes.`);
        this.buscar(this.numeroPagina());
      },
      error: e => this.fallo(e),
    });
  }

  protected confirmar(): void {
    const d = this.detalle();
    if (!d?.id || !window.confirm(`¿Confirmar ${d.consecutivo}? Se descuenta el stock de la bodega.`)) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.confirmar(d.id).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.detalle.set(r);
        this.avisos.ok(`Despacho ${r.consecutivo} confirmado: el stock ya se descontó.`);
        this.buscar(this.numeroPagina());
      },
      error: e => this.fallo(e),
    });
  }

  protected anular(): void {
    const d = this.detalle();
    const motivo = this.motivo().trim();
    if (!d?.id) {
      return;
    }
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo: mínimo 5 caracteres.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.anular(d.id, motivo).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.cancelarAnulacion();
        this.detalle.set(r);
        this.avisos.ok(r.confirmadoEn ? `Despacho ${r.consecutivo} anulado: el stock volvió a la bodega.` : `Despacho ${r.consecutivo} anulado.`);
        this.buscar(this.numeroPagina());
      },
      error: e => this.fallo(e),
    });
  }

  protected cancelarAnulacion(): void {
    this.anulando.set(false);
    this.motivo.set('');
  }

  private lineaVacia(): LineaForm {
    return { buscador: new BuscadorProducto(this.inventario), unidadId: null, cantidad: null };
  }

  /** Revisa lo mínimo; el backend vuelve a validar (existencias, unidades, productos activos). */
  private validar(): string | null {
    if (!this.bodegaId()) {
      return 'Elige la bodega de la que sale la mercancía.';
    }
    const ls = this.lineas();
    if (ls.some(l => !l.buscador.id())) {
      return 'Elige cada producto de la lista.';
    }
    if (ls.some(l => !l.cantidad || l.cantidad <= 0)) {
      return 'Cada línea necesita una cantidad mayor que cero.';
    }
    if (ls.some(l => !this.unidadDe(l))) {
      return 'Elige la unidad de cada línea.';
    }
    const ids = ls.map(l => l.buscador.id());
    if (new Set(ids).size !== ids.length) {
      return 'Un producto no puede repetirse: suma la cantidad en una sola línea.';
    }
    return null;
  }

  private fallo(e: unknown): void {
    this.trabajando.set(false);
    this.avisos.error(e);
  }
}
