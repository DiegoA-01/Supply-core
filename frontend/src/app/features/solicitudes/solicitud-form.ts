import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { Producto, Solicitud } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { codigoError, mensajeError } from '../../core/http/api';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';

/** Producto pedido en la solicitud. */
interface LineaPedida {
  producto: Producto;
  cantidad: number | null;
}

/**
 * Crear o editar una solicitud de compra (UC-04). Se guarda en borrador; el monto estimado lo calcula el backend
 * con el costo de referencia, y al enviarla valida presupuesto y duplicados.
 */
@Component({
  selector: 'sc-solicitud-form',
  imports: [FormsModule, RouterLink, Estado, PesosPipe, NumeroPipe],
  templateUrl: './solicitud-form.html',
  styleUrl: './solicitud-form.scss',
})
export class SolicitudForm implements OnInit {
  private readonly compras = inject(ComprasService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);

  /** Id de la solicitud a editar (de la ruta); vacío al crear. */
  readonly id = input<string>();

  protected readonly guardada = signal<Solicitud | null>(null);
  protected readonly motivo = signal('');
  protected readonly fecha = signal(iso(new Date(Date.now() + 7 * 86_400_000)));
  protected readonly lineas = signal<LineaPedida[]>([]);
  protected readonly busqueda = signal('');
  protected readonly resultados = signal<Producto[]>([]);
  protected readonly trabajando = signal(false);
  protected readonly sinGuardar = signal(false);
  /** El envío respondió que excede el presupuesto o que está duplicada: se puede reenviar con justificación. */
  protected readonly excede = signal<string | null>(null);
  protected readonly justificacion = signal('');
  protected readonly hoy = iso(new Date());

  protected readonly editable = computed(() => {
    const e = this.guardada()?.estado;
    return !e || e === 'BORRADOR' || e === 'DEVUELTA';
  });
  protected readonly devueltaCon = computed(() =>
    this.guardada()?.estado === 'DEVUELTA'
      ? [...(this.guardada()?.aprobaciones ?? [])].reverse().find(a => a.decision === 'DEVUELTA')?.observaciones ?? null
      : null);

  private readonly buscar$ = new Subject<string>();

  ngOnInit(): void {
    this.buscar$.pipe(debounceTime(250), distinctUntilChanged(), switchMap(t => this.inventario.productos({ texto: t, tamano: 10 })))
      .subscribe({ next: p => this.resultados.set(p.contenido ?? []), error: () => this.resultados.set([]) });
    const id = Number(this.id());
    if (id) {
      this.compras.obtener(id).subscribe({ next: s => this.aplicar(s), error: e => this.avisos.error(e) });
    }
  }

  /** @param texto lo que se busca (código o nombre) */
  protected buscar(texto: string): void {
    this.busqueda.set(texto);
    if (texto.trim().length >= 2) {
      this.buscar$.next(texto.trim());
    } else {
      this.resultados.set([]);
    }
  }

  /** @param p producto elegido de la búsqueda */
  protected agregar(p: Producto): void {
    if (!this.lineas().some(l => l.producto.id === p.id)) {
      this.lineas.update(l => [...l, { producto: p, cantidad: 1 }]);
      this.sinGuardar.set(true);
    }
    this.busqueda.set('');
    this.resultados.set([]);
  }

  /** @param i posición; @param cantidad cantidad pedida */
  protected cambiarCantidad(i: number, cantidad: number | null): void {
    this.lineas.update(l => l.map((x, j) => (j === i ? { ...x, cantidad } : x)));
    this.sinGuardar.set(true);
  }

  /** @param i posición de la línea a quitar */
  protected quitar(i: number): void {
    this.lineas.update(l => l.filter((_, j) => j !== i));
    this.sinGuardar.set(true);
  }

  /** Guarda el borrador; si {@code luegoEnviar}, lo manda a aprobación. */
  protected guardar(luegoEnviar = false): void {
    if (!this.motivo().trim() || !this.fecha() || !this.lineas().length || this.lineas().some(l => !l.cantidad || l.cantidad <= 0)) {
      this.avisos.ok('Completa la justificación, la fecha y al menos un producto con cantidad mayor que cero.');
      return;
    }
    const datos = {
      motivo: this.motivo().trim(),
      fechaRequerida: this.fecha(),
      lineas: this.lineas().map(l => ({ productoId: l.producto.id!, unidadMedidaId: l.producto.unidadMedida!.id!, cantidad: l.cantidad! })),
    };
    const id = this.guardada()?.id;
    this.trabajando.set(true);
    (id ? this.compras.actualizar(id, datos) : this.compras.crear(datos)).subscribe({
      next: s => {
        this.aplicar(s);
        this.sinGuardar.set(false);
        if (luegoEnviar) {
          this.enviar();
        } else {
          this.trabajando.set(false);
          this.avisos.ok(`${s.consecutivo} guardada como borrador.`);
          if (!id) {
            void this.router.navigate(['/app/solicitudes', s.id, 'editar'], { replaceUrl: true });
          }
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Envía a aprobación; con justificación, como excepción al presupuesto. */
  protected enviar(): void {
    const s = this.guardada();
    if (!s?.id) {
      return;
    }
    if (this.excede() && !this.justificacion().trim()) {
      this.avisos.ok('Escribe por qué se necesita aunque exceda el presupuesto.');
      return;
    }
    this.trabajando.set(true);
    this.compras.enviar(s.id, this.excede() ? this.justificacion().trim() : undefined).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.avisos.ok(`${r.consecutivo} enviada a aprobación.`);
        void this.router.navigate(['/app/solicitudes'], { queryParams: { texto: r.consecutivo } });
      },
      error: e => {
        this.trabajando.set(false);
        const codigo = codigoError(e);
        if (codigo === 'PRESUPUESTO_EXCEDIDO' || codigo === 'SOLICITUD_DUPLICADA') {
          // El backend explica el motivo; con una justificación se puede enviar igual y queda en la auditoría.
          this.excede.set(`${mensajeError(e)}${codigo === 'PRESUPUESTO_EXCEDIDO' ? ' Si la envías como excepción, la aprueba la política de mayor monto.' : ''}`);
          return;
        }
        this.avisos.error(e);
      },
    });
  }

  private aplicar(s: Solicitud): void {
    this.guardada.set(s);
    this.motivo.set(s.motivo ?? '');
    if (s.fechaRequerida) {
      this.fecha.set(s.fechaRequerida);
    }
    // Las líneas guardadas traen código, nombre y costo; se completan como producto para editarlas.
    this.lineas.set((s.lineas ?? []).map(l => ({
      producto: {
        id: l.productoId, codigo: l.productoCodigo, nombre: l.productoNombre, costoReferencia: l.costoReferencia,
        unidadMedida: { id: l.unidadMedidaId, codigo: l.unidadCodigo },
      },
      cantidad: l.cantidad ?? null,
    })));
  }
}
