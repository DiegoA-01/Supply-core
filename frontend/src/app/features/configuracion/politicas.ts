import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Politica, PoliticaNueva } from '../../core/api/modelos';
import { nombreRol } from '../../core/formato/etiquetas';
import { PesosPipe } from '../../core/formato/formato';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Políticas de aprobación: según el monto, qué rol aprueba una solicitud o una adjudicación. Cada política cubre un rango
 * [desde, hasta): el "desde" entra y el "hasta" no. Entre las activas del mismo tipo los rangos no se superponen, así cada
 * monto tiene una sola política. Si ningún rango cubre un monto, esa operación no necesita aprobación.
 */
@Component({
  selector: 'sc-politicas',
  imports: [FormsModule, RouterLink, PesosPipe],
  templateUrl: './politicas.html',
  styleUrl: './politicas.scss',
})
export class Politicas implements OnInit {
  private readonly servicio = inject(ConfiguracionService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Roles que pueden aprobar. */
  protected readonly roles = ['APROBADOR', 'ADMIN_COMPRAS'];
  protected readonly tipos: { valor: 'SOLICITUD' | 'ADJUDICACION'; titulo: string; ayuda: string }[] = [
    { valor: 'SOLICITUD', titulo: 'Solicitudes de compra', ayuda: 'Quién aprueba una solicitud según su monto estimado.' },
    { valor: 'ADJUDICACION', titulo: 'Adjudicaciones', ayuda: 'Quién aprueba la cotización elegida según su monto total.' },
  ];

  protected readonly lista = signal<Politica[] | null>(null);
  protected readonly abierto = signal(false);
  protected readonly editando = signal<Politica | null>(null);
  protected readonly trabajando = signal(false);
  protected readonly tipo = signal<'SOLICITUD' | 'ADJUDICACION'>('SOLICITUD');
  protected readonly rol = signal('APROBADOR');
  protected readonly desde = signal<number | null>(0);
  protected readonly hasta = signal<number | null>(null);

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.cargar();
  }

  /** @param t tipo; políticas de ese tipo de menor a mayor monto */
  protected de(t: string): Politica[] {
    return (this.lista() ?? []).filter(p => p.tipo === t).sort((a, b) => (a.montoDesde ?? 0) - (b.montoDesde ?? 0));
  }

  /** @param r código del rol; nombre para mostrar */
  protected nombre(r: string | undefined): string {
    return r ? nombreRol(r) : '';
  }

  /** @param t tipo con el que se abre el formulario nuevo */
  protected nueva(t: 'SOLICITUD' | 'ADJUDICACION'): void {
    this.editando.set(null);
    this.tipo.set(t);
    this.rol.set('APROBADOR');
    this.desde.set(0);
    this.hasta.set(null);
    this.abierto.set(true);
  }

  /** @param p política que se edita */
  protected editar(p: Politica): void {
    this.editando.set(p);
    this.tipo.set(p.tipo ?? 'SOLICITUD');
    this.rol.set(p.rolAprobador ?? 'APROBADOR');
    this.desde.set(p.montoDesde ?? 0);
    this.hasta.set(p.montoHasta ?? null);
    this.abierto.set(true);
  }

  protected guardar(): void {
    const desde = this.desde();
    const hasta = this.hasta();
    if (desde == null || desde < 0) {
      this.avisos.ok('Escribe desde qué monto aplica (0 o más).');
      return;
    }
    if (hasta != null && hasta <= desde) {
      this.avisos.ok('El monto hasta debe ser mayor que el monto desde (o déjalo vacío para "sin tope").');
      return;
    }
    const datos: PoliticaNueva = { tipo: this.tipo(), rolAprobador: this.rol(), montoDesde: desde, montoHasta: hasta ?? undefined };
    const p = this.editando();
    this.trabajando.set(true);
    (p?.id ? this.servicio.editarPolitica(p.id, datos) : this.servicio.crearPolitica(datos)).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.abierto.set(false);
        this.avisos.ok(p ? 'Política actualizada.' : 'Política creada.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param p política; la activa o la desactiva */
  protected alternar(p: Politica): void {
    if (!p.id) {
      return;
    }
    const desactivar = !!p.activo;
    if (desactivar && !window.confirm('¿Desactivar esta política? Los montos de su rango quedan sin aprobador hasta que haya otra activa que los cubra.')) {
      return;
    }
    this.servicio.cambiarPolitica(p.id, !desactivar).subscribe({
      next: () => {
        this.avisos.ok(desactivar ? 'Política desactivada.' : 'Política activada.');
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargar(): void {
    this.servicio.politicas().subscribe({
      next: l => this.lista.set(l),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
