import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { RfqResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/** Lista de RFQ: desde aquí se abre el análisis de cada una. */
@Component({
  selector: 'sc-rfq-lista',
  imports: [RouterLink, Estado, FechaPipe],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / Cotizaciones</p>
        <div class="encabezado">
          <div><h1>Cotizaciones con IA</h1><p>Elige una RFQ para ver el ranking que calculó el sistema y la explicación de la IA.</p></div>
          <div class="acciones">
            <div class="pastillas">
              @for (e of estados; track e.valor) {
                <button type="button" class="pastilla" [class.activa]="estado() === e.valor" (click)="filtrar(e.valor)">{{ e.texto }}</button>
              }
            </div>
            @if (esComprador()) {
              <a class="boton primario" routerLink="/app/cotizaciones/nueva"><span class="material-symbols-outlined">add</span> Nueva RFQ</a>
            }
          </div>
        </div>
      </div>
      <section class="tarjeta sin-relleno">
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>RFQ</th><th>Estado</th><th>Abierta</th><th>Fecha límite</th><th>Cerrada</th><th></th></tr></thead>
            <tbody>
              @for (r of lista() ?? []; track r.id) {
                <tr class="seleccionable" [routerLink]="['/app/cotizaciones', r.id]">
                  <td class="negrita">{{ r.consecutivo }}</td>
                  <td><sc-estado [valor]="r.estado" /></td>
                  <td>{{ r.abiertaEn | fecha }}</td>
                  <td>{{ r.fechaLimite | fecha: 'hora' }}</td>
                  <td>{{ r.cerradaEn | fecha }}</td>
                  <td class="num"><span class="material-symbols-outlined">chevron_right</span></td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ lista() ? 'No hay RFQ en este estado.' : 'Cargando…' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
})
export class RfqLista implements OnInit {
  private readonly servicio = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  /** Crea RFQ quien tiene el rol COMPRADOR. */
  protected readonly esComprador = computed(() => this.sesion.tieneRol(['COMPRADOR']) && !this.sesion.soloLectura());

  /** Texto que llega desde el buscador del encabezado. */
  readonly texto = input<string>();

  protected readonly estados = [
    { valor: 'CERRADA', texto: 'Por analizar' },
    { valor: 'BORRADOR', texto: 'Borradores' },
    { valor: 'ABIERTA', texto: 'Abiertas' },
    { valor: 'ADJUDICADA', texto: 'Adjudicadas' },
    { valor: null, texto: 'Todas' },
  ];
  protected readonly estado = signal<string | null>('CERRADA');
  protected readonly lista = signal<RfqResumen[] | null>(null);

  ngOnInit(): void {
    if (this.texto()) {
      this.estado.set(null);
    }
    this.cargar();
  }

  /** @param estado estado de la RFQ ({@code null} = todas) */
  protected filtrar(estado: string | null): void {
    this.estado.set(estado);
    this.cargar();
  }

  private cargar(): void {
    this.lista.set(null);
    this.servicio.rfqs({ estado: this.estado(), texto: this.texto() }).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
