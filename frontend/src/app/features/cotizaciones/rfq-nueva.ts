import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Proveedor, Rfq, Solicitud, SolicitudResumen } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ComprasService } from '../../core/servicios/compras.service';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { ReportesService, iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Crear una RFQ (UC-06) a partir de una solicitud aprobada. Se guarda en borrador para revisar a quién se invita;
 * al abrirla se envían las invitaciones y la solicitud pasa a "En cotización".
 */
@Component({
  selector: 'sc-rfq-nueva',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './rfq-nueva.html',
  styleUrl: './rfq-nueva.scss',
})
export class RfqNueva implements OnInit {
  private readonly compras = inject(ComprasService);
  private readonly cotizaciones = inject(CotizacionesService);
  private readonly reportes = inject(ReportesService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);

  /** Solicitud de origen (query param {@code ?solicitud=}). */
  readonly solicitud = input<string>();

  protected readonly aprobadas = signal<SolicitudResumen[] | null>(null);
  protected readonly elegida = signal<Solicitud | null>(null);
  protected readonly proveedores = signal<Proveedor[]>([]);
  protected readonly invitados = signal<Set<number>>(new Set());
  protected readonly especificaciones = signal<Record<number, string>>({});
  protected readonly fechaLimite = signal(this.porDefecto());
  protected readonly condiciones = signal('');
  protected readonly creada = signal<Rfq | null>(null);
  protected readonly trabajando = signal(false);
  /** Mínimo del campo de fecha: ahora, en hora local. */
  protected readonly ahora = `${iso(new Date())}T${`${new Date().getHours()}`.padStart(2, '0')}:${`${new Date().getMinutes()}`.padStart(2, '0')}`;

  protected readonly automatico = computed(() => this.invitados().size === 0);

  ngOnInit(): void {
    this.reportes.proveedores('HABILITADO').subscribe({ next: p => this.proveedores.set(p.contenido ?? []), error: () => undefined });
    const id = Number(this.solicitud());
    if (id) {
      this.elegir(id);
    } else {
      this.compras.buscar({ estado: 'APROBADA' }).subscribe({
        next: p => this.aprobadas.set(p.contenido ?? []),
        error: e => {
          this.aprobadas.set([]);
          this.avisos.error(e);
        },
      });
    }
  }

  /** @param id solicitud aprobada elegida */
  protected elegir(id: number): void {
    this.compras.obtener(id).subscribe({
      next: s => {
        if (s.estado !== 'APROBADA') {
          this.avisos.ok(`${s.consecutivo} está ${s.estado?.toLowerCase().replace('_', ' ')}: solo se cotiza una solicitud aprobada.`);
        }
        this.elegida.set(s);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param id proveedor que se marca o desmarca */
  protected alternar(id: number | undefined): void {
    if (id === undefined) {
      return;
    }
    this.invitados.update(s => {
      const n = new Set(s);
      if (n.has(id)) {
        n.delete(id);
      } else {
        n.add(id);
      }
      return n;
    });
  }

  /** @param productoId producto; @param texto especificación para los proveedores */
  protected especificar(productoId: number | undefined, texto: string): void {
    if (productoId !== undefined) {
      this.especificaciones.update(e => ({ ...e, [productoId]: texto }));
    }
  }

  protected crear(): void {
    const s = this.elegida();
    if (!s?.id || !this.fechaLimite()) {
      return;
    }
    if (new Date(this.fechaLimite()) <= new Date()) {
      this.avisos.ok('La fecha límite debe ser futura.');
      return;
    }
    const especificaciones = Object.entries(this.especificaciones())
      .filter(([, t]) => t.trim())
      .map(([productoId, texto]) => ({ productoId: Number(productoId), texto: texto.trim() }));
    this.trabajando.set(true);
    this.cotizaciones.crearRfq(s.id, {
      fechaLimite: `${this.fechaLimite()}:00`,
      condiciones: this.condiciones().trim() || undefined,
      proveedorIds: this.automatico() ? undefined : [...this.invitados()],
      especificaciones: especificaciones.length ? especificaciones : undefined,
    }).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.creada.set(r);
        this.avisos.ok(`${r.consecutivo} creada en borrador. Revisa a quién se invita y ábrela.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected abrir(): void {
    const r = this.creada();
    if (!r?.id) {
      return;
    }
    this.trabajando.set(true);
    this.cotizaciones.abrirRfq(r.id).subscribe({
      next: a => {
        this.avisos.ok(`${a.consecutivo} abierta: se enviaron ${a.invitados?.length ?? 0} invitaciones.`);
        void this.router.navigate(['/app/cotizaciones', a.id]);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Dentro de 5 días a las 17:00, en el formato del campo {@code datetime-local}. */
  private porDefecto(): string {
    const f = new Date(Date.now() + 5 * 86_400_000);
    return `${iso(f)}T17:00`;
  }
}
