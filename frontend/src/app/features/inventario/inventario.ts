import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CargaMasiva, Categoria, InventarioItem, InventarioResumen, Producto, SugerenciaReorden, UnidadMedida } from '../../core/api/modelos';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/** Fila de la tabla: viene del reporte de inventario o, si el rol no lo ve, del listado de productos. */
type Fila = Pick<InventarioItem, 'productoId' | 'codigo' | 'nombre' | 'categoria' | 'unidad' | 'stock' | 'stockMinimo' | 'stockMaximo' | 'valor' | 'bajoMinimo'>;

/**
 * Vista 7 · Productos y stock. El stock no se edita aquí: solo cambia con recepciones, ajustes con motivo y
 * traslados. La reorden la calcula el backend y solo deja un borrador.
 */
@Component({
  selector: 'sc-inventario',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, PesosPipe, NumeroPipe],
  templateUrl: './inventario.html',
  styleUrl: './inventario.scss',
})
export class Inventario implements OnInit {
  private readonly servicio = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly resumen = signal<InventarioResumen | null>(null);
  protected readonly filas = signal<Fila[] | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  protected readonly sugerencias = signal<SugerenciaReorden[]>([]);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly soloBajo = signal(false);
  protected readonly panel = signal<'ninguno' | 'carga' | 'nuevo' | 'reorden' | 'inactivos'>('ninguno');
  protected readonly archivo = signal<File | null>(null);
  protected readonly carga = signal<CargaMasiva | null>(null);
  protected readonly trabajando = signal(false);
  /** Producto que se está editando ({@code null} = el formulario crea uno nuevo). */
  protected readonly editandoId = signal<number | null>(null);
  protected readonly inactivos = signal<Producto[] | null>(null);

  protected readonly conReporte = computed(() => this.sesion.tieneRol(['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'ALMACENISTA']) && this.sesion.tieneModulo('REPORTES'));
  protected readonly esAdminCompras = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  protected readonly esSolicitante = computed(() => this.sesion.tieneRol(['SOLICITANTE']) && !this.sesion.soloLectura());
  protected readonly porReponer = computed(() => this.sugerencias().filter(s => !s.yaCubierto && (s.cantidadSugerida ?? 0) > 0));
  protected readonly totalBajo = computed(() => this.resumen()?.bajoMinimo ?? this.filas()?.filter(f => f.bajoMinimo).length ?? 0);
  protected readonly sinExistencias = computed(() => (this.filas() ?? []).filter(f => (f.stock ?? 0) <= 0).length);
  protected readonly ejemplo = computed(() => this.porReponer()[0] ?? null);

  protected readonly nuevo = inject(FormBuilder).nonNullable.group({
    codigo: ['', [Validators.required, Validators.maxLength(40)]],
    nombre: ['', [Validators.required, Validators.maxLength(150)]],
    categoriaId: [0, [Validators.required, Validators.min(1)]],
    unidadMedidaId: [0, [Validators.required, Validators.min(1)]],
    stockMinimo: [0, [Validators.required, Validators.min(0)]],
    stockMaximo: [null as number | null],
    costoReferencia: [null as number | null],
  });

  ngOnInit(): void {
    this.servicio.categorias().subscribe({ next: l => this.categorias.set(l), error: () => undefined });
    this.servicio.sugerencias().subscribe({ next: l => this.sugerencias.set(l), error: () => undefined });
    this.cargar();
  }

  /** @param bajo solo los que están bajo el mínimo */
  protected filtrarBajo(bajo: boolean): void {
    this.soloBajo.set(bajo);
    this.cargar();
  }

  /** @param id categoría ({@code null} = todas) */
  protected filtrarCategoria(id: number | null): void {
    this.categoriaId.set(id);
    this.cargar();
  }

  /** @param f fila; ancho de la barra de stock frente al máximo (solo dibujo) */
  protected ancho(f: Fila): number {
    const tope = f.stockMaximo || (f.stockMinimo ?? 0) * 2 || 1;
    return Math.min(100, Math.round(((f.stock ?? 0) / tope) * 100));
  }

  /** @param f fila */
  protected estado(f: Fila): { texto: string; tono: string } {
    if ((f.stock ?? 0) <= 0) {
      return { texto: 'Sin existencias', tono: 'rojo' };
    }
    return f.bajoMinimo ? { texto: 'Bajo el mínimo', tono: 'ambar' } : { texto: 'Normal', tono: 'verde' };
  }

  /** @param p panel que se abre (o se cierra si ya estaba abierto) */
  protected abrir(p: 'carga' | 'nuevo' | 'reorden' | 'inactivos'): void {
    this.panel.set(this.panel() === p ? 'ninguno' : p);
    if (p === 'nuevo') {
      this.editandoId.set(null);
      this.nuevo.reset({ codigo: '', nombre: '', categoriaId: 0, unidadMedidaId: 0, stockMinimo: 0, stockMaximo: null, costoReferencia: null });
      this.cargarUnidades();
    }
    if (p === 'inactivos' && this.panel() === 'inactivos') {
      this.cargarInactivos();
    }
  }

  /** @param f fila; abre el formulario con los datos del producto para corregirlos (el stock no se edita aquí) */
  protected editar(f: Fila): void {
    if (!f.productoId) {
      return;
    }
    this.cargarUnidades();
    this.servicio.producto(f.productoId).subscribe({
      next: p => {
        this.editandoId.set(p.id ?? null);
        this.nuevo.reset({
          codigo: p.codigo ?? '', nombre: p.nombre ?? '', categoriaId: p.categoria?.id ?? 0, unidadMedidaId: p.unidadMedida?.id ?? 0,
          stockMinimo: p.stockMinimo ?? 0, stockMaximo: p.stockMaximo ?? null, costoReferencia: p.costoReferencia ?? null,
        });
        this.panel.set('nuevo');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param f fila; desactiva el producto (deja de ofrecerse; su historial y stock se conservan) */
  protected desactivar(f: Fila): void {
    if (!f.productoId || !window.confirm(`¿Desactivar "${f.nombre}"? Deja de ofrecerse en solicitudes y cotizaciones; su historial se conserva.`)) {
      return;
    }
    this.servicio.cambiarEstadoProducto(f.productoId, false).subscribe({
      next: () => {
        this.avisos.ok(`${f.nombre} quedó inactivo.`);
        this.cargar();
        if (this.panel() === 'inactivos') {
          this.cargarInactivos();
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p producto inactivo; lo vuelve a activar */
  protected activar(p: Producto): void {
    if (!p.id) {
      return;
    }
    this.servicio.cambiarEstadoProducto(p.id, true).subscribe({
      next: () => {
        this.avisos.ok(`${p.nombre} quedó activo.`);
        this.cargarInactivos();
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargarUnidades(): void {
    if (!this.unidades().length) {
      this.servicio.unidades().subscribe({ next: l => this.unidades.set(l), error: () => undefined });
    }
  }

  private cargarInactivos(): void {
    this.inactivos.set(null);
    this.servicio.productos({ activo: false }).subscribe({ next: p => this.inactivos.set(p.contenido ?? []), error: e => { this.inactivos.set([]); this.avisos.error(e); } });
  }

  protected exportarPorCorreo(): void {
    this.servicio.stockPorCorreo().subscribe({
      next: r => this.avisos.ok(`Enviamos ${r.archivo ?? 'el Excel'} a ${r.para}.`),
      error: e => this.avisos.error(e),
    });
  }

  protected exportar(): void {
    this.servicio.exportarStock().subscribe({ next: b => descargar(b, 'inventario-stock.xlsx'), error: e => this.avisos.error(e) });
  }

  protected bajarPlantilla(): void {
    this.servicio.plantilla().subscribe({ next: b => descargar(b, 'plantilla-productos.xlsx'), error: e => this.avisos.error(e) });
  }

  /** @param evento cambio del selector de archivos */
  protected elegirArchivo(evento: Event): void {
    this.archivo.set((evento.target as HTMLInputElement).files?.[0] ?? null);
    this.carga.set(null);
  }

  /** @param soloValidar primero se revisa; luego se aplica todo o nada */
  protected cargarExcel(soloValidar: boolean): void {
    const a = this.archivo();
    if (!a) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.cargaMasiva(a, soloValidar).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.carga.set(r);
        if (r.aplicado) {
          this.avisos.ok(`Carga aplicada: ${r.creados} creados, ${r.actualizados} actualizados.`);
          this.cargar();
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected crearProducto(): void {
    if (this.nuevo.invalid) {
      this.nuevo.markAllAsTouched();
      return;
    }
    const v = this.nuevo.getRawValue();
    const datos = { ...v, stockMaximo: v.stockMaximo ?? undefined, costoReferencia: v.costoReferencia ?? undefined };
    const id = this.editandoId();
    this.trabajando.set(true);
    (id ? this.servicio.actualizarProducto(id, datos) : this.servicio.crear(datos)).subscribe({
      next: p => {
        this.trabajando.set(false);
        this.avisos.ok(`Producto ${p.codigo} ${id ? 'actualizado' : 'creado'}.`);
        this.editandoId.set(null);
        this.nuevo.reset();
        this.panel.set('ninguno');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected crearBorrador(): void {
    const fecha = new Date();
    fecha.setDate(fecha.getDate() + 7);
    this.trabajando.set(true);
    this.servicio.borradorReorden(iso(fecha), this.porReponer().map(s => s.productoId!)).subscribe({
      next: s => {
        this.trabajando.set(false);
        this.avisos.ok(`Borrador ${s.consecutivo} creado. Revísalo y envíalo cuando quieras.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.filas.set(null);
    if (this.conReporte()) {
      this.servicio.resumen(this.categoriaId(), this.soloBajo()).subscribe({
        next: r => {
          this.resumen.set(r);
          this.filas.set(r.items ?? []);
        },
        error: e => {
          this.filas.set([]);
          this.avisos.error(e);
        },
      });
      return;
    }
    this.servicio.productos({ categoriaId: this.categoriaId() }).subscribe({
      next: p => this.filas.set((p.contenido ?? [])
        .filter(x => !this.soloBajo() || x.bajoStockMinimo)
        .map(x => ({
          productoId: x.id, codigo: x.codigo, nombre: x.nombre, categoria: x.categoria?.nombre, unidad: x.unidadMedida?.codigo,
          stock: x.stockTotal, stockMinimo: x.stockMinimo, stockMaximo: x.stockMaximo, bajoMinimo: x.bajoStockMinimo,
        }))),
      error: e => {
        this.filas.set([]);
        this.avisos.error(e);
      },
    });
  }
}
