import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Contrato, ContratoNuevo, ContratoResumen, Pagina, Proveedor } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { ReportesService } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { BuscadorProducto } from '../../core/ui/buscador-producto';

/** Línea del formulario: un producto con su precio pactado y su tope. */
interface PrecioForm {
  buscador: BuscadorProducto;
  precio: number | null;
  impuesto: number | null;
  tope: number | null;
}

/**
 * Contratos con precios pactados: con un proveedor, por un tiempo, el precio de cada producto (y, si se quiere, hasta qué
 * cantidad). Con un contrato vigente que cubre una solicitud aprobada, el comprador emite la orden directa sin cotizar.
 * Lo consumido y lo disponible de cada tope lo calcula el backend. Un contrato no se borra: se anula con motivo.
 */
@Component({
  selector: 'sc-contratos',
  imports: [FormsModule, RouterLink, Estado, FechaPipe, NumeroPipe, PesosPipe],
  templateUrl: './contratos.html',
  styleUrl: './contratos.scss',
})
export class Contratos implements OnInit {
  private readonly servicio = inject(ConfiguracionService);
  private readonly inventario = inject(InventarioService);
  private readonly reportes = inject(ReportesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pagina = signal<Pagina<ContratoResumen> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly proveedores = signal<Proveedor[]>([]);
  protected readonly detalle = signal<Contrato | null>(null);
  protected readonly trabajando = signal(false);

  // Filtros
  protected readonly proveedorFiltro = signal<number | null>(null);
  protected readonly estadoFiltro = signal<string | null>(null);
  protected readonly soloVigentes = signal(false);

  // Formulario nuevo
  protected readonly abierto = signal(false);
  protected readonly proveedorId = signal<number | null>(null);
  protected readonly numero = signal('');
  protected readonly descripcion = signal('');
  protected readonly desde = signal('');
  protected readonly hasta = signal('');
  protected readonly precios = signal<PrecioForm[]>([]);

  // Cambios sobre uno existente
  protected readonly nuevaVigencia = signal('');
  protected readonly cambiandoVigencia = signal(false);
  protected readonly anulando = signal(false);
  protected readonly motivo = signal('');

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.reportes.proveedores('HABILITADO').subscribe({ next: p => this.proveedores.set(p.contenido ?? []), error: () => undefined });
    this.buscar(0);
  }

  /** @param p número de página desde 0 */
  protected buscar(p: number): void {
    this.pagina.set(null);
    this.numeroPagina.set(p);
    this.servicio.contratos({ proveedorId: this.proveedorFiltro(), estado: this.estadoFiltro(), vigente: this.soloVigentes() ? true : null }, p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  /** @param c contrato de la lista; abre su detalle con los precios */
  protected abrir(c: ContratoResumen): void {
    this.abierto.set(false);
    this.cancelarCambios();
    this.servicio.contrato(c.id!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  protected nuevo(): void {
    this.detalle.set(null);
    this.proveedorId.set(null);
    this.numero.set('');
    this.descripcion.set('');
    this.desde.set('');
    this.hasta.set('');
    this.precios.set([this.precioVacio()]);
    this.abierto.set(true);
  }

  protected agregarPrecio(): void {
    this.precios.update(l => [...l, this.precioVacio()]);
  }

  /** @param i posición de la línea que se quita */
  protected quitarPrecio(i: number): void {
    this.precios.update(l => (l.length > 1 ? l.filter((_, k) => k !== i) : l));
  }

  /** @param i línea; @param campo columna; @param valor número escrito */
  protected cambiarPrecio(i: number, campo: 'precio' | 'impuesto' | 'tope', valor: number | null): void {
    this.precios.update(l => l.map((x, k) => (k === i ? { ...x, [campo]: valor } : x)));
  }

  protected guardar(): void {
    const error = this.validar();
    if (error) {
      this.avisos.ok(error);
      return;
    }
    const datos: ContratoNuevo = {
      proveedorId: this.proveedorId()!, numero: this.numero().trim(), descripcion: this.descripcion().trim() || undefined,
      vigenteDesde: this.desde(), vigenteHasta: this.hasta(),
      precios: this.precios().map(p => ({ productoId: p.buscador.id()!, precioUnitario: p.precio!, impuestoPct: p.impuesto ?? 0, cantidadMaxima: p.tope ?? undefined })),
    };
    this.trabajando.set(true);
    this.servicio.crearContrato(datos).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.abierto.set(false);
        this.detalle.set(c);
        this.avisos.ok(`Contrato ${c.numero} creado.`);
        this.buscar(0);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected abrirVigencia(): void {
    this.nuevaVigencia.set(this.detalle()?.vigenteHasta ?? '');
    this.cambiandoVigencia.set(true);
    this.anulando.set(false);
  }

  protected guardarVigencia(): void {
    const d = this.detalle();
    if (!d?.id || !this.nuevaVigencia()) {
      this.avisos.ok('Escribe la nueva fecha de fin.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.cambiarVigencia(d.id, this.nuevaVigencia()).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.cancelarCambios();
        this.detalle.set(c);
        this.avisos.ok('Vigencia actualizada.');
        this.buscar(this.numeroPagina());
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected anular(): void {
    const d = this.detalle();
    const motivo = this.motivo().trim();
    if (!d?.id) {
      return;
    }
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo de la anulación (mínimo 5 caracteres).');
      return;
    }
    this.trabajando.set(true);
    this.servicio.anularContrato(d.id, motivo).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.cancelarCambios();
        this.detalle.set(c);
        this.avisos.ok(`Contrato ${c.numero} anulado: ya no se ofrece para órdenes directas.`);
        this.buscar(this.numeroPagina());
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected cancelarCambios(): void {
    this.cambiandoVigencia.set(false);
    this.anulando.set(false);
    this.motivo.set('');
  }

  private precioVacio(): PrecioForm {
    return { buscador: new BuscadorProducto(this.inventario), precio: null, impuesto: 19, tope: null };
  }

  /** Revisa lo mínimo; el backend vuelve a validar (vigencia en orden, productos sin repetir, número único). */
  private validar(): string | null {
    if (!this.proveedorId()) {
      return 'Elige el proveedor.';
    }
    if (!this.numero().trim()) {
      return 'Escribe el número del contrato.';
    }
    if (!this.desde() || !this.hasta()) {
      return 'Escribe desde cuándo y hasta cuándo vale.';
    }
    if (this.hasta() < this.desde()) {
      return 'El contrato no puede terminar antes de empezar.';
    }
    const ps = this.precios();
    if (ps.some(p => !p.buscador.id())) {
      return 'Elige cada producto de la lista.';
    }
    if (ps.some(p => !p.precio || p.precio <= 0)) {
      return 'Cada producto necesita un precio mayor que cero.';
    }
    if (ps.some(p => p.impuesto != null && (p.impuesto < 0 || p.impuesto > 100))) {
      return 'El impuesto va entre 0 y 100.';
    }
    const ids = ps.map(p => p.buscador.id());
    if (new Set(ids).size !== ids.length) {
      return 'Un producto no puede repetirse en el mismo contrato.';
    }
    return null;
  }
}
