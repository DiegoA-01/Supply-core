import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Delegacion, DelegadoCandidato } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { DelegacionesService } from '../../core/servicios/delegaciones.service';
import { iso } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { mensajeError } from '../../core/http/api';

/**
 * Delegación de aprobaciones: quien aprueba (solicitudes y adjudicaciones) deja a otra persona decidir por él durante unas
 * fechas, por ejemplo en vacaciones. El delegado ve esas pendientes y puede decidir, salvo las que el propio delegante
 * originó. Solo delega quien tiene un rol que aprueba en alguna política activa.
 */
@Component({
  selector: 'sc-delegaciones',
  imports: [FormsModule, RouterLink, Estado, FechaPipe],
  templateUrl: './delegaciones.html',
  styleUrl: './delegaciones.scss',
})
export class Delegaciones implements OnInit {
  private readonly servicio = inject(DelegacionesService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly otorgadas = signal<Delegacion[] | null>(null);
  protected readonly recibidas = signal<Delegacion[] | null>(null);
  protected readonly candidatos = signal<DelegadoCandidato[]>([]);
  /** Mensaje cuando el usuario no puede delegar (no tiene un rol que apruebe). */
  protected readonly sinPermiso = signal<string | null>(null);
  protected readonly abierto = signal(false);
  protected readonly trabajando = signal(false);

  protected readonly delegadoId = signal<number | null>(null);
  protected readonly desde = signal(iso(new Date()));
  protected readonly hasta = signal('');
  protected readonly motivo = signal('');

  ngOnInit(): void {
    this.cargar();
    this.servicio.candidatos().subscribe({
      next: l => this.candidatos.set(l),
      error: e => this.sinPermiso.set(mensajeError(e)),
    });
  }

  protected nueva(): void {
    this.delegadoId.set(null);
    this.desde.set(iso(new Date()));
    this.hasta.set('');
    this.motivo.set('');
    this.abierto.set(true);
  }

  protected guardar(): void {
    if (!this.delegadoId()) {
      this.avisos.ok('Elige a quién delegas.');
      return;
    }
    if (!this.desde() || !this.hasta()) {
      this.avisos.ok('Escribe desde cuándo y hasta cuándo.');
      return;
    }
    if (this.hasta() < this.desde()) {
      this.avisos.ok('La delegación no puede terminar antes de empezar.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.crear({ delegadoId: this.delegadoId()!, vigenteDesde: this.desde(), vigenteHasta: this.hasta(), motivo: this.motivo().trim() || undefined }).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.abierto.set(false);
        this.avisos.ok(`Delegaste en ${d.delegadoNombre}. Le avisamos.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param d delegación que di; la revoca (deja de valer desde ya y el delegado recibe aviso) */
  protected revocar(d: Delegacion): void {
    if (!d.id || !window.confirm(`¿Revocar la delegación a ${d.delegadoNombre}? Dejará de poder decidir por ti desde ahora.`)) {
      return;
    }
    this.servicio.revocar(d.id).subscribe({
      next: () => {
        this.avisos.ok('Delegación revocada.');
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargar(): void {
    this.servicio.otorgadas().subscribe({ next: l => this.otorgadas.set(l), error: e => { this.otorgadas.set([]); this.avisos.error(e); } });
    this.servicio.recibidas().subscribe({ next: l => this.recibidas.set(l), error: () => this.recibidas.set([]) });
  }
}
