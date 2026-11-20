import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EstadoPedidoVenta, Pagina, PedidoVenta } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { VentaService } from '../../core/servicios/venta.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/** Texto y color de cada estado. */
const ESTADOS: Record<EstadoPedidoVenta, { texto: string; tono: string }> = {
  PENDIENTE_COBRO: { texto: 'Por cobrar', tono: 'ambar' }, COBRADO: { texto: 'Cobrado', tono: 'verde' }, DESCARTADO: { texto: 'Descartado', tono: 'gris' },
};

/**
 * Pedidos de venta (spec 004/005): el vendedor ve los suyos; el cajero ve los pendientes y los cobra en el POS. Un
 * pedido no guarda montos: al cobrarlo se usan los precios del día.
 */
@Component({
  selector: 'sc-pedidos-venta',
  imports: [FormsModule, RouterLink, FechaPipe, NumeroPipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Pedidos de venta</h1>
        <p>Pedidos armados por los vendedores, sin cobrar. No descuentan stock hasta que el cajero los cobra.</p></div>
        <a class="boton primario" routerLink="/app/pos"><span class="material-symbols-outlined">add_shopping_cart</span> {{ cobra() ? 'Ir al POS' : 'Nuevo pedido' }}</a></div>
      <section class="tarjeta sin-relleno">
        <div class="filtros">
          <label for="pv-estado">Estado</label>
          <select id="pv-estado" class="entrada" [ngModel]="estado()" (ngModelChange)="estado.set($event); buscar()">
            <option [ngValue]="null">Todos</option>
            @for (e of estados; track e) { <option [ngValue]="e">{{ texto(e) }}</option> }
          </select>
        </div>
        <div class="tabla-contenedor"><table class="tabla">
          <thead><tr><th>Pedido</th><th>Fecha</th><th>Cliente</th><th>Vendedor</th><th>Productos</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            @for (p of pagina()?.contenido ?? []; track p.id) {
              <tr>
                <td><strong>{{ p.consecutivo }}</strong>@if (p.nota) { <span class="sub">{{ p.nota }}</span> }</td>
                <td>{{ p.creadoEn | fecha: 'hora' }}</td><td>{{ p.cliente }}</td><td>{{ p.vendedor }}</td>
                <td>@for (l of p.lineas ?? []; track $index) { <span class="sub">{{ l.cantidad | numero }} {{ l.unidad }} · {{ l.nombre }}</span> }</td>
                <td><span class="chip {{ tono(p.estado) }}">{{ texto(p.estado) }}</span></td>
                <td class="num">@if (p.estado === 'PENDIENTE_COBRO') {
                  <div class="fila fin">
                    @if (cobra()) { <a class="boton chico primario" routerLink="/app/pos" [queryParams]="{ pedido: p.id }">Cobrar</a> }
                    <button type="button" class="boton chico" (click)="descartar(p)">Descartar</button>
                  </div>
                }</td>
              </tr>
            } @empty {
              <tr><td colspan="7" class="vacio">{{ pagina() ? 'No hay pedidos con este filtro.' : 'Cargando…' }}</td></tr>
            }
          </tbody>
        </table></div>
      </section>
    </div>
  `,
  styles: `
    .filtros { display: flex; gap: 10px; align-items: center; padding: 14px 20px; border-bottom: 1px solid var(--sc-borde); select { max-width: 220px; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .fin { justify-content: flex-end; gap: 6px; }
    .chip.gris { background: #efefef; color: var(--sc-texto-2); }
  `,
})
export class PedidosVenta implements OnInit {
  private readonly ventas = inject(VentaService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  protected readonly estados = Object.keys(ESTADOS) as EstadoPedidoVenta[];
  protected readonly estado = signal<EstadoPedidoVenta | null>('PENDIENTE_COBRO');
  protected readonly pagina = signal<Pagina<PedidoVenta> | null>(null);
  protected readonly cobra = computed(() => this.sesion.tieneRol(['CAJERO', 'SUPERVISOR_VENTAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.buscar();
  }

  protected buscar(): void {
    this.ventas.pedidos(this.estado()).subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /** @param p pedido pendiente */
  protected descartar(p: PedidoVenta): void {
    this.ventas.descartarPedido(p.id!).subscribe({
      next: () => {
        this.avisos.ok(`Pedido ${p.consecutivo} descartado.`);
        this.buscar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param e estado @returns texto */
  protected texto(e: EstadoPedidoVenta | undefined): string {
    return e ? ESTADOS[e].texto : '';
  }

  /** @param e estado @returns color */
  protected tono(e: EstadoPedidoVenta | undefined): string {
    return e ? ESTADOS[e].tono : 'gris';
  }
}
