import { Component, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { OrdenResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/** Lista de órdenes de compra vigentes. */
@Component({
  selector: 'sc-ordenes-lista',
  imports: [RouterLink, Estado, FechaPipe, PesosPipe],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / Órdenes</p>
        <div class="encabezado">
          <div><h1>Órdenes y pagos</h1><p>Una orden emitida no se edita: los cambios crean una versión nueva.</p></div>
          <div class="acciones pastillas">
            @if (sesion.tieneRol(['COMPRADOR']) && !sesion.soloLectura()) {
              <a class="boton chico" routerLink="/app/ordenes/directa"><span class="material-symbols-outlined">bolt</span> Orden directa</a>
            }
            @for (e of estados; track e.texto) {
              <button type="button" class="pastilla" [class.activa]="estado() === e.valor" (click)="filtrar(e.valor)">{{ e.texto }}</button>
            }
          </div>
        </div>
      </div>
      <section class="tarjeta sin-relleno">
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Orden</th><th>Proveedor</th><th>Estado</th><th class="oculto-movil">Destino</th><th>Entrega</th><th class="num">Total</th></tr></thead>
            <tbody>
              @for (o of lista() ?? []; track o.id) {
                <tr class="seleccionable" [routerLink]="['/app/ordenes', o.id]">
                  <td class="negrita">{{ o.consecutivo }}<span class="sub">versión {{ o.version }}</span></td>
                  <td>{{ o.proveedorRazonSocial }}</td>
                  <td><sc-estado [valor]="o.estado" /></td>
                  <td class="oculto-movil">{{ o.bodegaDestinoNombre }}</td>
                  <td>{{ o.fechaEntregaEsperada | fecha }}</td>
                  <td class="num">{{ o.total | pesos }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ lista() ? 'No hay órdenes con este filtro.' : 'Cargando…' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class OrdenesLista implements OnInit {
  protected readonly sesion = inject(SesionService);
  private readonly servicio = inject(OrdenesService);
  private readonly avisos = inject(Avisos);

  /** Texto que llega desde el buscador del encabezado. */
  readonly texto = input<string>();

  protected readonly estados = [
    { valor: null, texto: 'Todas' },
    { valor: 'EMITIDA', texto: 'Emitidas' },
    { valor: 'DESPACHADA', texto: 'Despachadas' },
    { valor: 'RECIBIDA_PARCIAL', texto: 'Recibidas parcial' },
    { valor: 'RECIBIDA_TOTAL', texto: 'Recibidas' },
  ];
  protected readonly estado = signal<string | null>(null);
  protected readonly lista = signal<OrdenResumen[] | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  /** @param estado estado de la orden ({@code null} = todas) */
  protected filtrar(estado: string | null): void {
    this.estado.set(estado);
    this.cargar();
  }

  private cargar(): void {
    this.lista.set(null);
    this.servicio.buscar({ estado: this.estado(), texto: this.texto() }).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
