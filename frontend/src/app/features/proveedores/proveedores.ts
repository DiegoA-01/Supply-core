import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { Categoria, Evaluacion, Pagina, Proveedor } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PuntajePipe } from '../../core/formato/formato';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { ReportesService } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Proveedores de la empresa. Se registran solos en el portal y solo cotizan cuando compras los habilita. Compras puede
 * habilitar, rechazar, suspender (y volver a habilitar), corregir el contacto y las categorías en que cotizan, y ver las
 * calificaciones que han recibido. El score lo calcula el backend al cerrar cada recepción.
 */
@Component({
  selector: 'sc-proveedores',
  imports: [FormsModule, RouterLink, Estado, FechaPipe, NumeroPipe, PuntajePipe],
  templateUrl: './proveedores.html',
  styleUrl: './proveedores.scss',
})
export class Proveedores implements OnInit {
  private readonly reportes = inject(ReportesService);
  private readonly servicio = inject(ConfiguracionService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);
  private readonly ruta = inject(ActivatedRoute, { optional: true });

  protected readonly estados = [
    { valor: null, texto: 'Todos' },
    { valor: 'PENDIENTE', texto: 'Por habilitar' },
    { valor: 'HABILITADO', texto: 'Habilitados' },
    { valor: 'SUSPENDIDO', texto: 'Suspendidos' },
    { valor: 'RECHAZADO', texto: 'Rechazados' },
  ];
  protected readonly estado = signal<string | null>(null);
  protected readonly lista = signal<Proveedor[] | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly detalle = signal<Proveedor | null>(null);
  protected readonly trabajando = signal(false);

  // Edición de contacto y categorías
  protected readonly editando = signal(false);
  protected readonly correo = signal('');
  protected readonly telefono = signal('');
  protected readonly direccion = signal('');
  protected readonly categoriaIds = signal<number[]>([]);

  // Cambio de estado con motivo (rechazar o suspender)
  protected readonly motivoPara = signal<'rechazar' | 'suspender' | null>(null);
  protected readonly motivo = signal('');

  // Calificaciones
  protected readonly evaluaciones = signal<Pagina<Evaluacion> | null>(null);
  protected readonly paginaEval = signal(0);

  /** Código de la empresa: con él entran los proveedores al portal. */
  protected readonly codigoEmpresa = computed(() => this.sesion.empresa()?.codigoPublico);

  protected readonly puedeDecidir = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.inventario.categorias().subscribe({ next: l => this.categorias.set(l), error: () => undefined });
    // Llegar desde un aviso (?estado=PENDIENTE) abre la lista ya filtrada
    const pedido = this.ruta?.snapshot.queryParamMap.get('estado');
    if (pedido && this.estados.some(e => e.valor === pedido)) {
      this.estado.set(pedido);
    }
    this.cargar();
  }

  /** @param texto texto que se copia al portapapeles */
  protected copiar(texto: string): void {
    navigator.clipboard?.writeText(texto).then(() => this.avisos.ok('Copiado'), () => undefined);
  }

  /** @param estado estado del proveedor ({@code null} = todos) */
  protected filtrar(estado: string | null): void {
    this.estado.set(estado);
    this.cargar();
  }

  /** @param p proveedor de la lista; abre su ficha y sus calificaciones */
  protected abrir(p: Proveedor): void {
    this.editando.set(false);
    this.cancelarMotivo();
    this.evaluaciones.set(null);
    this.servicio.proveedor(p.id!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
    this.cargarEvaluaciones(p.id!, 0);
  }

  /** @param id proveedor; @param pagina número de página desde 0 */
  protected cargarEvaluaciones(id: number, pagina: number): void {
    this.paginaEval.set(pagina);
    this.servicio.evaluaciones(id, pagina).subscribe({ next: e => this.evaluaciones.set(e), error: () => this.evaluaciones.set(null) });
  }

  protected habilitar(): void {
    const p = this.detalle();
    if (!p?.id) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.habilitarProveedor(p.id).subscribe({
      next: r => this.terminado(r, `${r.razonSocial} quedó habilitado y ya puede cotizar.`),
      error: e => this.fallo(e),
    });
  }

  /** Rechaza o suspende con el motivo escrito (es obligatorio). */
  protected confirmarMotivo(): void {
    const p = this.detalle();
    const que = this.motivoPara();
    const motivo = this.motivo().trim();
    if (!p?.id || !que) {
      return;
    }
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo (mínimo 5 caracteres).');
      return;
    }
    this.trabajando.set(true);
    const peticion = que === 'rechazar' ? this.reportes.rechazar(p.id, motivo) : this.servicio.suspenderProveedor(p.id, motivo);
    peticion.subscribe({
      next: r => {
        this.cancelarMotivo();
        this.terminado(r, que === 'rechazar' ? `${r.razonSocial} quedó rechazado.` : `${r.razonSocial} quedó suspendido: ya no puede cotizar.`);
      },
      error: e => this.fallo(e),
    });
  }

  protected cancelarMotivo(): void {
    this.motivoPara.set(null);
    this.motivo.set('');
  }

  protected editar(): void {
    const p = this.detalle();
    if (!p) {
      return;
    }
    this.correo.set(p.correoContacto ?? '');
    this.telefono.set(p.telefono ?? '');
    this.direccion.set(p.direccion ?? '');
    this.categoriaIds.set((p.categorias ?? []).map(c => c.id!));
    this.editando.set(true);
  }

  /** @param id categoría; la marca o la desmarca */
  protected alternarCategoria(id: number): void {
    this.categoriaIds.update(l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]));
  }

  protected guardar(): void {
    const p = this.detalle();
    if (!p?.id) {
      return;
    }
    if (!this.categoriaIds().length) {
      this.avisos.ok('Elige al menos una categoría: de ahí salen las invitaciones a cotizar.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.editarProveedor(p.id, {
      correoContacto: this.correo().trim() || undefined, telefono: this.telefono().trim() || undefined,
      direccion: this.direccion().trim() || undefined, categoriaIds: this.categoriaIds(),
    }).subscribe({
      next: r => {
        this.editando.set(false);
        this.terminado(r, 'Datos del proveedor actualizados.');
      },
      error: e => this.fallo(e),
    });
  }

  private terminado(r: Proveedor, mensaje: string): void {
    this.trabajando.set(false);
    this.detalle.set(r);
    this.avisos.ok(mensaje);
    this.cargar();
  }

  private fallo(e: unknown): void {
    this.trabajando.set(false);
    this.avisos.error(e);
  }

  private cargar(): void {
    this.lista.set(null);
    this.reportes.proveedores(this.estado()).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
