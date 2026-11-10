import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RfqResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { PortalService } from '../../core/servicios/portal.service';
import { Avisos } from '../../core/ui/avisos';

/** Portal · RFQ a las que el proveedor fue invitado. */
@Component({
  selector: 'sc-portal-rfqs',
  imports: [RouterLink, Estado, FechaPipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Cotizaciones</h1><p>Solicitudes de cotización a las que te invitaron.</p></div></div>
      <div class="rejilla tarjetas">
        @for (r of lista() ?? []; track r.id) {
          <a class="tarjeta rfq" [routerLink]="['/portal/cotizaciones', r.id]">
            <div class="separado"><strong>{{ r.consecutivo }}</strong><sc-estado [valor]="r.estado" /></div>
            <span class="texto-2">Cierra el {{ r.fechaLimite | fecha: 'hora' }}</span>
            <span class="boton chico">{{ r.estado === 'ABIERTA' ? 'Cotizar' : 'Ver' }}</span>
          </a>
        } @empty {
          <p class="tarjeta vacio">{{ lista() ? 'No tienes invitaciones por ahora.' : 'Cargando…' }}</p>
        }
      </div>
    </div>
  `,
  styles: `
    .tarjetas { grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); }
    .rfq { display: flex; flex-direction: column; gap: 8px; color: inherit; text-decoration: none; }
    .rfq:hover { border-color: #f0b9a4; }
    .rfq .boton { align-self: flex-start; }
  `,
})
export class PortalRfqs implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly avisos = inject(Avisos);

  protected readonly lista = signal<RfqResumen[] | null>(null);

  ngOnInit(): void {
    this.portal.rfqs().subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
