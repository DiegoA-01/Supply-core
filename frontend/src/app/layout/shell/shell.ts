import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map } from 'rxjs';

import { Notificacion } from '../../core/api/modelos';
import { FechaPipe, iniciales } from '../../core/formato/formato';
import { nombreRol } from '../../core/formato/etiquetas';
import { MENU, OpcionMenu } from '../../core/navegacion/menu';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { NotificacionesService } from '../../core/servicios/notificaciones.service';
import { SesionService } from '../../core/sesion/sesion.service';

/** Marco de la empresa: menú lateral según roles y módulos, buscador, notificaciones y usuario. */
@Component({
  selector: 'sc-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, FormsModule, MatMenuModule, MatTooltipModule, FechaPipe],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit {
  protected readonly sesion = inject(SesionService);
  protected readonly notificaciones = inject(NotificacionesService);
  private readonly compras = inject(ComprasService);
  private readonly inventario = inject(InventarioService);
  private readonly router = inject(Router);
  private readonly destruir = inject(DestroyRef);

  /** Pantalla angosta: el menú se vuelve un panel que se abre con el botón. */
  protected readonly movil = toSignal(inject(BreakpointObserver).observe('(max-width: 1023px)').pipe(map(r => r.matches)), { initialValue: false });
  protected readonly menuAbierto = signal(false);
  protected readonly busqueda = signal('');
  protected readonly recientes = signal<Notificacion[]>([]);
  protected readonly contadores = signal<Record<string, number>>({});

  /** Secciones con solo las opciones que el rol del usuario puede usar. */
  protected readonly secciones = computed(() => MENU
    .map(s => ({ ...s, opciones: s.opciones.filter(o => this.sesion.tieneRol(o.roles)) }))
    .filter(s => s.opciones.length > 0));

  protected readonly iniciales = computed(() => iniciales(this.sesion.usuario()?.nombre));
  protected readonly rolPrincipal = computed(() => {
    const roles = this.sesion.usuario()?.roles ?? [];
    return roles.length ? `${nombreRol(roles[0])} · ${roles[0]}` : '';
  });

  ngOnInit(): void {
    this.router.events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed(this.destruir))
      .subscribe(() => this.menuAbierto.set(false));
    this.notificaciones.contar();
    this.cargarContadores();
  }

  /** @param opcion opción del menú */
  protected bloqueada(opcion: OpcionMenu): boolean {
    return !this.sesion.tieneModulo(opcion.modulo);
  }

  /** Busca por consecutivo: OC-…, SOL-… o RFQ-… abre el listado filtrado. */
  protected buscar(): void {
    const texto = this.busqueda().trim();
    if (!texto) {
      return;
    }
    const t = texto.toUpperCase();
    const ruta = t.startsWith('SOL') ? '/app/solicitudes' : t.startsWith('RFQ') ? '/app/cotizaciones' : '/app/ordenes';
    void this.router.navigate([ruta], { queryParams: { texto } });
    this.busqueda.set('');
  }

  /** Abre la campana y trae las notificaciones recientes. */
  protected abrirNotificaciones(): void {
    this.notificaciones.recientes().subscribe({ next: p => this.recientes.set(p.contenido ?? []), error: () => undefined });
  }

  protected marcarLeidas(): void {
    this.notificaciones.marcarTodas().subscribe({
      next: () => this.recientes.update(l => l.map(n => ({ ...n, leida: true }))),
      error: () => undefined,
    });
  }

  private cargarContadores(): void {
    if (this.sesion.tieneModulo('COMPRAS')) {
      this.compras.pendientes(1).subscribe({
        next: p => this.contadores.update(c => ({ ...c, solicitudes: p.totalElementos ?? 0 })),
        error: () => undefined,
      });
    }
    if (this.sesion.tieneModulo('INVENTARIO')) {
      this.inventario.alertas().subscribe({
        next: l => this.contadores.update(c => ({ ...c, bajoMinimo: l.length })),
        error: () => undefined,
      });
    }
  }
}
