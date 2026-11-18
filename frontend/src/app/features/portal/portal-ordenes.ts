import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, forkJoin, of } from 'rxjs';

import { Factura, Orden, OrdenResumen, PagosOrden, ResumenRecepcion } from '../../core/api/modelos';
import { Estado, EtiquetaPipe } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { PortalService } from '../../core/servicios/portal.service';
import { Avisos } from '../../core/ui/avisos';

/** Portal · órdenes del proveedor (UC-12): aceptar, preparar y despachar; y lo que le han pagado. */
@Component({
  selector: 'sc-portal-ordenes',
  imports: [FormsModule, Estado, EtiquetaPipe, PesosPipe, NumeroPipe, FechaPipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Órdenes y pagos</h1><p>Confirma, prepara y despacha tus órdenes. Los pagos los registra el cliente.</p></div></div>
      <div class="rejilla principal">
        <section class="tarjeta sin-relleno">
          <div class="tabla-contenedor">
            <table class="tabla">
              <thead><tr><th>Orden</th><th>Estado</th><th>Entrega</th><th class="num">Total</th></tr></thead>
              <tbody>
                @for (o of lista() ?? []; track o.id) {
                  <tr class="seleccionable" [class.seleccionada]="orden()?.id === o.id" (click)="abrir(o)">
                    <td class="negrita">{{ o.consecutivo }}<span class="sub">{{ o.bodegaDestinoNombre }}</span></td>
                    <td><sc-estado [valor]="o.estado" /></td>
                    <td>{{ o.fechaEntregaEsperada | fecha }}</td>
                    <td class="num">{{ o.total | pesos }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="4" class="vacio">{{ lista() ? 'Aún no tienes órdenes.' : 'Cargando…' }}</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        @if (orden(); as o) {
          <section class="tarjeta">
            <div class="separado"><h2>{{ o.consecutivo }}</h2><sc-estado [valor]="o.estado" /></div>
            <p class="texto-2">Entregar en {{ o.lugarEntrega }} antes del {{ o.fechaEntregaEsperada | fecha }}.</p>
            @for (l of o.lineas; track l.id) {
              <div class="separado linea"><span>{{ l.productoNombre }}</span><span>{{ l.cantidad | numero }} {{ l.unidad }}</span></div>
            }
            <div class="pares">
              <div class="par"><span>Total</span><span>{{ o.total | pesos }}</span></div>
              @if (pagos(); as p) {
                <div class="par"><span>Te han pagado</span><span>{{ p.pagado | pesos }}</span></div>
                <div class="par"><span>Saldo</span><span>{{ p.saldo | pesos }}</span></div>
              }
              @for (f of facturas(); track f.id) {
                <div class="par"><span>Factura {{ f.numero }}</span><span><sc-estado [valor]="f.estado" /></span></div>
              }
            </div>

            @if (entregas(); as r) {
              @if (r.recepciones?.length) {
                <h3>Lo que el cliente ya recibió</h3>
                <div class="tabla-contenedor">
                  <table class="tabla">
                    <thead><tr><th>Producto</th><th class="num">Pedido</th><th class="num">Aceptado</th><th class="num">Rechazado</th><th class="num">Falta</th></tr></thead>
                    <tbody>
                      @for (a of r.lineas ?? []; track a.ordenLineaId) {
                        <tr><td>{{ a.productoNombre }}</td><td class="num">{{ a.pedido | numero }}</td><td class="num">{{ a.aceptado | numero }}</td>
                          <td class="num">{{ a.rechazado | numero }}</td><td class="num">{{ a.faltante | numero }}</td></tr>
                      }
                    </tbody>
                  </table>
                </div>
                @for (rec of r.recepciones; track rec.id) {
                  @for (l of rec.lineas ?? []; track l.id) {
                    @if ((l.cantidadRechazada ?? 0) > 0) {
                      <p class="texto-chico rechazo">{{ rec.recibidoEn | fecha }} · se rechazaron {{ l.cantidadRechazada | numero }} de {{ l.productoNombre }}
                        ({{ l.motivoRechazo | etiqueta }}){{ l.observacionCalidad ? ': ' + l.observacionCalidad : '' }}</p>
                    }
                  }
                }
              }
            }

            @switch (o.estado) {
              @case ('EMITIDA') {
                <div class="campo margen"><label for="obs">Motivo (solo si la rechazas)</label>
                  <input id="obs" class="entrada" [ngModel]="observacion()" (ngModelChange)="observacion.set($event)"></div>
                <div class="fila margen">
                  <button type="button" class="boton primario" [disabled]="trabajando()" (click)="accion('aceptar')">Aceptar orden</button>
                  <button type="button" class="boton peligro" [disabled]="trabajando()" (click)="accion('rechazar')">Rechazar</button>
                </div>
              }
              @case ('ACEPTADA_PROVEEDOR') {
                <button type="button" class="boton primario margen" [disabled]="trabajando()" (click)="accion('preparar')">Marcar en preparación</button>
              }
              @case ('EN_PREPARACION') {
                <div class="campo margen"><label for="guia">Guía o remisión del despacho</label>
                  <input id="guia" class="entrada" [ngModel]="observacion()" (ngModelChange)="observacion.set($event)" placeholder="Ej. Guía 12345 · Transportes del Valle"></div>
                <button type="button" class="boton primario margen" [disabled]="trabajando()" (click)="accion('despachar')">Despachar</button>
              }
            }
          </section>
        }
      </div>
    </div>
  `,
  styles: `
    .linea { padding: 8px 0; border-bottom: 1px solid #f3f3f3; font-size: 14px; }
    .margen { margin-top: 14px; }
    h3 { margin: 16px 0 6px; }
    .rechazo { margin: 4px 0; color: var(--sc-rojo); }
    h2 { margin: 0; }
  `,
})
export class PortalOrdenes implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly avisos = inject(Avisos);

  protected readonly lista = signal<OrdenResumen[] | null>(null);
  protected readonly orden = signal<Orden | null>(null);
  protected readonly pagos = signal<PagosOrden | null>(null);
  protected readonly facturas = signal<Factura[]>([]);
  protected readonly entregas = signal<ResumenRecepcion | null>(null);
  protected readonly observacion = signal('');
  protected readonly trabajando = signal(false);

  ngOnInit(): void {
    this.cargar();
  }

  /** @param o orden de la lista */
  protected abrir(o: OrdenResumen): void {
    this.observacion.set('');
    forkJoin({
      orden: this.portal.orden(o.id!),
      pagos: this.portal.pagos(o.id!).pipe(catchError(() => of(null))),
      facturas: this.portal.facturas(o.id!).pipe(catchError(() => of([] as Factura[]))),
      entregas: this.portal.recepciones(o.id!).pipe(catchError(() => of(null))),
    }).subscribe({
      next: r => {
        this.orden.set(r.orden);
        this.pagos.set(r.pagos);
        this.facturas.set(r.facturas);
        this.entregas.set(r.entregas);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param a acción sobre la orden */
  protected accion(a: 'aceptar' | 'rechazar' | 'preparar' | 'despachar'): void {
    const o = this.orden();
    if (!o?.id) {
      return;
    }
    if (a === 'rechazar' && !this.observacion().trim()) {
      this.avisos.ok('Escribe el motivo del rechazo.');
      return;
    }
    this.trabajando.set(true);
    this.portal.accion(o.id, a, this.observacion().trim()).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.orden.set(r);
        this.avisos.ok('Listo. El comprador ya fue notificado.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.portal.ordenes(null).subscribe({
      next: p => {
        const l = p.contenido ?? [];
        this.lista.set(l);
        if (!this.orden() && l.length) {
          this.abrir(l[0]!);
        }
      },
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
