import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Adjudicacion, Pagina, PoliticaAplicable } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { AdjudicacionesService } from '../../core/servicios/adjudicaciones.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Adjudicaciones por aprobar: cuando el comprador elige una cotización y el monto pasa de lo que puede decidir solo, la
 * aprueba o la rechaza quien tenga el rol de la política (o quien lo recibió por delegación). Quien adjudicó no puede
 * aprobar la suya. El monto y la política los calcula el backend.
 */
@Component({
  selector: 'sc-adjudicaciones',
  imports: [FormsModule, RouterLink, Estado, FechaPipe, PesosPipe],
  templateUrl: './adjudicaciones.html',
  styleUrl: './adjudicaciones.scss',
})
export class Adjudicaciones implements OnInit {
  private readonly servicio = inject(AdjudicacionesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly pagina = signal<Pagina<Adjudicacion> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly detalle = signal<Adjudicacion | null>(null);
  protected readonly politica = signal<PoliticaAplicable | null>(null);
  protected readonly observaciones = signal('');
  protected readonly trabajando = signal(false);
  protected readonly resultado = signal<Adjudicacion | null>(null);

  /** Quien adjudicó no aprueba la suya; el backend también lo impide. */
  protected readonly esMia = computed(() => this.detalle()?.usuarioId === this.sesion.usuario()?.id);
  protected readonly puedeDecidir = computed(() => this.detalle()?.estado === 'PENDIENTE_APROBACION' && !this.esMia() && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.cargar(0);
  }

  /** @param p número de página desde 0 */
  protected cargar(p: number): void {
    this.pagina.set(null);
    this.numeroPagina.set(p);
    this.servicio.pendientes(p).subscribe({
      next: r => {
        this.pagina.set(r);
        const actual = this.detalle();
        if (actual && !(r.contenido ?? []).some(a => a.id === actual.id)) {
          this.detalle.set(null);
        }
      },
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 20, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  /** @param a adjudicación de la lista; abre su detalle y busca qué rol la aprueba */
  protected abrir(a: Adjudicacion): void {
    this.resultado.set(null);
    this.observaciones.set('');
    this.detalle.set(a);
    this.politica.set(null);
    if (a.montoTotal != null) {
      this.servicio.politicaAplicable('ADJUDICACION', a.montoTotal).subscribe({ next: p => this.politica.set(p), error: () => undefined });
    }
  }

  /** @param aprobar {@code true} aprueba; {@code false} rechaza (y exige el motivo) */
  protected decidir(aprobar: boolean): void {
    const a = this.detalle();
    const texto = this.observaciones().trim();
    if (!a?.id) {
      return;
    }
    if (!aprobar && texto.length < 5) {
      this.avisos.ok('Para rechazar escribe el motivo (mínimo 5 caracteres).');
      return;
    }
    this.trabajando.set(true);
    this.servicio.decidir(a.id, aprobar, texto).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.resultado.set(r);
        this.detalle.set(null);
        this.avisos.ok(aprobar ? 'Adjudicación aprobada: el comprador ya puede emitir la orden.' : 'Adjudicación rechazada.');
        this.cargar(this.numeroPagina());
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
