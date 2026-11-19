import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EstadoPedidoInterno, Pagina, PedidoInterno } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { InventarioService } from '../../core/servicios/inventario.service';
import { PedidosService } from '../../core/servicios/pedidos.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { BuscadorProducto } from '../../core/ui/buscador-producto';
import { SelectorSede } from '../../core/ui/selector-sede';

/** Texto y color de cada estado. */
const ESTADOS: Record<EstadoPedidoInterno, { texto: string; tono: string }> = {
  PENDIENTE: { texto: 'Pendiente', tono: 'ambar' },
  EN_GESTION: { texto: 'En gestión', tono: 'naranja' },
  ATENDIDO: { texto: 'Atendido', tono: 'verde' },
  DESCARTADO: { texto: 'Descartado', tono: 'gris' },
};

/** Respuesta de compras en edición. */
interface Respuesta {
  id: number;
  estado: EstadoPedidoInterno;
  texto: string;
}

/**
 * Pedidos internos ("Necesito este producto"): cualquier persona de la empresa avisa a compras de algo que hace falta;
 * compras lo toma, lo atiende o lo descarta con una respuesta. Los proveedores nunca ven estos pedidos.
 */
@Component({
  selector: 'sc-pedidos',
  imports: [FormsModule, RouterLink, FechaPipe, NumeroPipe, SelectorSede],
  templateUrl: './pedidos.html',
  styleUrl: './pedidos.scss',
})
export class PedidosInternos implements OnInit {
  private readonly pedidos = inject(PedidosService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Producto con el que se abre el formulario (query param {@code ?productoId=}). */
  readonly productoInicial = input<string>('', { alias: 'productoId' });

  protected readonly buscador = new BuscadorProducto(this.inventario);
  protected readonly descripcion = signal('');
  protected readonly cantidad = signal<number | null>(null);
  protected readonly sede = signal<number | null>(null);
  protected readonly nota = signal('');
  protected readonly enviando = signal(false);

  protected readonly pagina = signal<Pagina<PedidoInterno> | null>(null);
  protected readonly vista = signal<'mios' | 'todos'>('mios');
  protected readonly filtro = signal<EstadoPedidoInterno | null>(null);
  protected readonly respuesta = signal<Respuesta | null>(null);
  protected readonly estados = Object.keys(ESTADOS) as EstadoPedidoInterno[];

  /** Compras, auditoría y el administrador ven todos los pedidos. */
  protected readonly veTodo = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR', 'ADMIN_SISTEMA']));
  /** Solo compras responde. */
  protected readonly responde = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    if (this.veTodo()) {
      this.vista.set('todos');
    }
    const id = Number(this.productoInicial());
    if (id) {
      this.inventario.producto(id).subscribe({ next: p => this.buscador.elegir(p), error: () => undefined });
    }
    this.cargar();
  }

  protected cargar(): void {
    this.pedidos.listar(this.vista() === 'mios', this.filtro()).subscribe({
      next: p => this.pagina.set(p),
      error: e => this.avisos.error(e),
    });
  }

  /** @param v qué pedidos ver */
  protected cambiarVista(v: 'mios' | 'todos'): void {
    this.vista.set(v);
    this.cargar();
  }

  protected pedir(): void {
    const productoId = this.buscador.id();
    const descripcion = this.descripcion().trim();
    if (!productoId && !descripcion) {
      this.avisos.ok('Elige un producto de la lista o describe lo que hace falta.');
      return;
    }
    const cantidad = this.cantidad();
    if (cantidad !== null && !(cantidad > 0)) {
      this.avisos.ok('La cantidad debe ser mayor que cero, o déjala vacía.');
      return;
    }
    this.enviando.set(true);
    this.pedidos.crear({ productoId, descripcion: productoId ? null : descripcion, cantidad, sedeId: this.sede(),
      nota: this.nota().trim() || null }).subscribe({
      next: p => {
        this.enviando.set(false);
        this.avisos.ok(`${p.consecutivo} enviado a compras. Te avisaremos cuando lo respondan.`);
        this.buscador.escribir('');
        this.descripcion.set('');
        this.cantidad.set(null);
        this.nota.set('');
        this.cargar();
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param p pedido; @param estado respuesta que se va a dar */
  protected responder(p: PedidoInterno, estado: EstadoPedidoInterno): void {
    if (estado === 'EN_GESTION') {
      this.guardarRespuesta({ id: p.id!, estado, texto: '' });
      return;
    }
    this.respuesta.set({ id: p.id!, estado, texto: '' });
  }

  /** @param r respuesta a enviar */
  protected guardarRespuesta(r: Respuesta): void {
    if (r.estado === 'DESCARTADO' && !r.texto.trim()) {
      this.avisos.ok('Explica por qué no se hará el pedido.');
      return;
    }
    this.pedidos.gestionar(r.id, r.estado, r.texto.trim() || null).subscribe({
      next: p => {
        this.respuesta.set(null);
        this.avisos.ok(`${p.consecutivo}: ${this.etiqueta(p.estado).toLowerCase()}.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param texto respuesta escrita */
  protected escribirRespuesta(texto: string): void {
    this.respuesta.update(r => (r ? { ...r, texto } : r));
  }

  /** @param e estado; texto */
  protected etiqueta(e: EstadoPedidoInterno | undefined): string {
    return e ? ESTADOS[e].texto : '—';
  }

  /** @param e estado; color */
  protected tono(e: EstadoPedidoInterno | undefined): string {
    return e ? ESTADOS[e].tono : 'gris';
  }

  /** @param e estado; {@code true} si ya no se puede responder */
  protected cerrado(e: EstadoPedidoInterno | undefined): boolean {
    return e === 'ATENDIDO' || e === 'DESCARTADO';
  }
}
