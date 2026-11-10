import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { OrdenResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { Avisos } from '../../core/ui/avisos';

/** Órdenes que esperan mercancía: despachadas o recibidas en parte. */
@Component({
  selector: 'sc-recepcion-lista',
  imports: [RouterLink, Estado, FechaPipe, PesosPipe],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / Recepción</p>
        <div class="encabezado"><div><h1>Recepción</h1><p>Órdenes despachadas o con entregas pendientes. La recepción es lo único que sube el stock por compra.</p></div></div>
      </div>
      <div class="rejilla tarjetas">
        @for (o of lista() ?? []; track o.id) {
          <a class="tarjeta orden" [routerLink]="['/app/recepcion', o.id]">
            <div class="separado"><strong>{{ o.consecutivo }}</strong><sc-estado [valor]="o.estado" /></div>
            <span>{{ o.proveedorRazonSocial }}</span>
            <span class="texto-chico texto-2">Destino {{ o.bodegaDestinoNombre }} · esperada {{ o.fechaEntregaEsperada | fecha: 'corta' }}</span>
            <span class="texto-chico texto-2">Total {{ o.total | pesos }}</span>
            <span class="boton chico">Recibir</span>
          </a>
        } @empty {
          <p class="tarjeta vacio">{{ lista() ? 'No hay mercancía por recibir.' : 'Cargando…' }}</p>
        }
      </div>
    </div>
  `,
  styles: `
    .tarjetas { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
    .orden { display: flex; flex-direction: column; gap: 6px; color: inherit; text-decoration: none; }
    .orden:hover { border-color: #f0b9a4; }
    .orden .boton { align-self: flex-start; margin-top: 6px; }
  `,
})
export class RecepcionLista implements OnInit {
  private readonly servicio = inject(OrdenesService);
  private readonly avisos = inject(Avisos);

  protected readonly lista = signal<OrdenResumen[] | null>(null);

  ngOnInit(): void {
    forkJoin([this.servicio.buscar({ estado: 'DESPACHADA' }), this.servicio.buscar({ estado: 'RECIBIDA_PARCIAL' })]).subscribe({
      next: ([a, b]) => this.lista.set([...(a.contenido ?? []), ...(b.contenido ?? [])]),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
