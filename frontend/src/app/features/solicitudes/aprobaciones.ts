import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';

import { CentroCosto, DecisionSolicitud, Politica, Presupuesto, Solicitud, SolicitudResumen } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe, horasDesde } from '../../core/formato/formato';
import { ComprasService } from '../../core/servicios/compras.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

type Pestana = 'pendientes' | 'APROBADA' | 'todas';

/**
 * Vista 3 · Solicitudes por aprobar (UC-05). Bandeja a la izquierda, detalle y decisión a la derecha.
 * El backend valida quién puede decidir (política, delegación, segregación) y lo deja en la auditoría.
 */
@Component({
  selector: 'sc-aprobaciones',
  imports: [FormsModule, RouterLink, Estado, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './aprobaciones.html',
  styleUrl: './aprobaciones.scss',
})
export class Aprobaciones implements OnInit {
  private readonly compras = inject(ComprasService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Texto de búsqueda que llega desde el buscador del encabezado. */
  readonly texto = input<string>();

  protected readonly pestana = signal<Pestana>('pendientes');
  protected readonly lista = signal<SolicitudResumen[] | null>(null);
  protected readonly totalPendientes = signal(0);
  protected readonly seleccionada = signal<Solicitud | null>(null);
  protected readonly presupuesto = signal<Presupuesto | null>(null);
  protected readonly solicitante = signal<string | null>(null);
  protected readonly observaciones = signal('');
  protected readonly enviando = signal(false);
  private readonly centros = signal<Map<number, CentroCosto>>(new Map());
  private readonly politicas = signal<Map<number, Politica>>(new Map());

  protected readonly centro = computed(() => this.centros().get(this.seleccionada()?.centroCostoId ?? -1));
  protected readonly politica = computed(() => this.politicas().get(this.seleccionada()?.politicaAprobacionId ?? -1));
  protected readonly horasLimite = computed(() => this.sesion.empresa()?.slaAprobacionHoras ?? 24);
  protected readonly esSolicitante = computed(() => this.sesion.tieneRol(['SOLICITANTE']) && !this.sesion.soloLectura());
  /** Solo pide, no aprueba: la página abre en sus solicitudes. */
  protected readonly soloPide = computed(() => this.esSolicitante() && !this.sesion.tieneRol(['APROBADOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR']));
  /** La solicitud abierta es del usuario de la sesión. */
  protected readonly esMia = computed(() => this.seleccionada()?.usuarioSolicitanteId === this.sesion.usuario()?.id);
  protected readonly puedeDecidir = computed(() => this.seleccionada()?.estado === 'PENDIENTE_APROBACION' && !this.esMia() && !this.sesion.soloLectura());
  protected readonly puedeEditar = computed(() => this.esMia() && this.esSolicitante()
    && (this.seleccionada()?.estado === 'BORRADOR' || this.seleccionada()?.estado === 'DEVUELTA'));
  protected readonly puedeCancelar = computed(() => {
    const e = this.seleccionada()?.estado;
    const abierta = e === 'BORRADOR' || e === 'DEVUELTA' || e === 'PENDIENTE_APROBACION' || e === 'APROBADA';
    return abierta && !this.sesion.soloLectura() && ((this.esMia() && this.esSolicitante()) || this.sesion.tieneRol(['ADMIN_COMPRAS']));
  });
  protected readonly puedeCrearRfq = computed(() => this.seleccionada()?.estado === 'APROBADA'
    && this.sesion.tieneRol(['COMPRADOR']) && this.sesion.tieneModulo('RFQ_COTIZACIONES') && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.compras.centros().subscribe({ next: l => this.centros.set(new Map(l.map(c => [c.id!, c]))), error: () => undefined });
    this.compras.politicas().subscribe({ next: l => this.politicas.set(new Map(l.map(p => [p.id!, p]))), error: () => undefined });
    if (this.texto() || this.soloPide()) {
      this.pestana.set('todas');
    }
    this.cargar();
  }

  /** Cancela la solicitud abierta (el solicitante las suyas; compras cualquiera abierta). */
  protected cancelar(): void {
    const s = this.seleccionada();
    if (!s?.id || !window.confirm(`¿Cancelar ${s.consecutivo}? No se puede deshacer.`)) {
      return;
    }
    this.compras.cancelar(s.id).subscribe({
      next: r => {
        this.seleccionada.set(r);
        this.avisos.ok(`${r.consecutivo} cancelada.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p pestaña */
  protected cambiar(p: Pestana): void {
    this.pestana.set(p);
    this.cargar();
  }

  /** @param s solicitud de la lista */
  protected abrir(s: SolicitudResumen): void {
    this.observaciones.set('');
    this.presupuesto.set(null);
    this.solicitante.set(null);
    this.compras.obtener(s.id!).subscribe({
      next: sol => {
        this.seleccionada.set(sol);
        this.cargarPresupuesto(sol);
        if (this.sesion.tieneRol(['ADMIN_SISTEMA', 'AUDITOR'])) {
          this.compras.usuario(sol.usuarioSolicitanteId!).pipe(catchError(() => of(null)))
            .subscribe(u => this.solicitante.set(u?.nombreCompleto ?? null));
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param s solicitud; horas desde que se envió */
  protected horas(s: SolicitudResumen): number | null {
    return s.estado === 'PENDIENTE_APROBACION' ? horasDesde(s.enviadoEn) : null;
  }

  /** @param d decisión elegida */
  protected decidir(d: DecisionSolicitud): void {
    const sol = this.seleccionada();
    if (!sol?.id) {
      return;
    }
    if (d !== 'APROBADA' && !this.observaciones().trim()) {
      this.avisos.ok('Escribe las observaciones: son obligatorias al devolver o rechazar.');
      return;
    }
    this.enviando.set(true);
    this.compras.decidir(sol.id, d, this.observaciones().trim()).subscribe({
      next: r => {
        this.enviando.set(false);
        this.avisos.ok(`${r.consecutivo}: ${d === 'APROBADA' ? 'aprobada' : d === 'DEVUELTA' ? 'devuelta al solicitante' : 'rechazada'}.`);
        this.seleccionada.set(r);
        this.cargar();
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** Estado y texto que está usando la lista, para exportar lo mismo que se ve. */
  private filtrosActuales(): { estado: string | null; texto: string | undefined } {
    const p = this.pestana();
    return { estado: p === 'pendientes' ? 'PENDIENTE_APROBACION' : p === 'todas' ? null : p, texto: this.texto() };
  }

  protected exportarExcel(): void {
    this.compras.exportarSolicitudes(this.filtrosActuales()).subscribe({
      next: b => descargar(b, 'solicitudes.xlsx'),
      error: e => this.avisos.error(e),
    });
  }

  protected exportarPorCorreo(): void {
    this.compras.solicitudesPorCorreo(this.filtrosActuales()).subscribe({
      next: r => this.avisos.ok(`Enviamos ${r.archivo ?? 'el Excel'} a ${r.para}.`),
      error: e => this.avisos.error(e),
    });
  }

  private cargar(): void {
    this.lista.set(null);
    const p = this.pestana();
    const peticion = p === 'pendientes' ? this.compras.pendientes()
      : this.compras.buscar({ estado: p === 'todas' ? null : p, texto: this.texto() });
    peticion.subscribe({
      next: r => {
        const l = r.contenido ?? [];
        this.lista.set(l);
        if (p === 'pendientes') {
          this.totalPendientes.set(r.totalElementos ?? l.length);
        }
        if (l.length && !this.seleccionada()) {
          this.abrir(l[0]!);
        }
      },
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
    if (p !== 'pendientes') {
      this.compras.pendientes(1).subscribe({ next: r => this.totalPendientes.set(r.totalElementos ?? 0), error: () => undefined });
    }
  }

  private cargarPresupuesto(sol: Solicitud): void {
    if (!sol.centroCostoId || !sol.fechaRequerida || !this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'AUDITOR'])) {
      return;
    }
    const [anio, mes] = sol.fechaRequerida.split('-').map(Number);
    this.compras.presupuestos(sol.centroCostoId, anio!).subscribe({
      next: l => this.presupuesto.set(l.find(p => p.mes === mes) ?? null),
      error: () => undefined,
    });
  }
}
