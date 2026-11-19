import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Proveedor } from '../../core/api/modelos';
import { iniciales } from '../../core/formato/formato';
import { PortalService } from '../../core/servicios/portal.service';
import { SesionService } from '../../core/sesion/sesion.service';

/** Marco del portal del proveedor: barra superior con sus secciones. */
@Component({
  selector: 'sc-portal-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="barra-superior">
      <a class="marca" routerLink="/portal/cotizaciones">
        <img src="logo.svg" alt="" width="32" height="32"><span>Supply-Core</span>
      </a>
      <span class="chip oculto-movil">Portal del proveedor</span>
      <nav aria-label="Secciones del portal">
        <a routerLink="/portal/cotizaciones" routerLinkActive="activa">Cotizaciones</a>
        <a routerLink="/portal/ordenes" routerLinkActive="activa">Órdenes y pagos</a>
        <a routerLink="/portal/catalogo" routerLinkActive="activa">Mi catálogo</a>
        <a routerLink="/portal/perfil" routerLinkActive="activa">Mi perfil</a>
      </nav>
      <div class="quien">
        <span class="avatar">{{ siglas() }}</span>
        <span class="datos oculto-movil">
          <strong>{{ proveedor()?.razonSocial ?? sesion.usuario()?.nombre }}</strong>
          <span>{{ sesion.usuario()?.correo }}</span>
        </span>
        <button type="button" class="boton chico" (click)="sesion.salir()">Salir</button>
      </div>
    </header>
    <main class="contenido"><router-outlet /></main>
  `,
  styles: `
    :host { display: block; min-height: 100vh; background: #f3f3f3; }
    .barra-superior { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
      padding: 12px clamp(16px, 3vw, 48px); background: #fff; border-bottom: 1px solid var(--sc-borde); }
    .marca { display: flex; align-items: center; gap: 10px; font-weight: 700; font-size: 19px; color: var(--sc-texto); text-decoration: none; }
    nav { display: flex; gap: 4px; flex-wrap: wrap; }
    nav a { padding: 10px 16px; border-radius: 12px; color: var(--sc-texto); text-decoration: none; font-weight: 500; }
    nav a.activa { background: var(--sc-primario-suave); color: var(--sc-primario); }
    .quien { margin-left: auto; display: flex; align-items: center; gap: 10px; }
    .datos { display: flex; flex-direction: column; font-size: 13px; color: var(--sc-texto-2); strong { font-size: 15px; color: var(--sc-texto); } }
    .avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: var(--sc-primario-suave); color: #8a3a1c; font-weight: 600; font-size: 14px; }
    .contenido { max-width: 1180px; margin: 0 auto; padding: 24px 16px 40px; }
  `,
})
export class PortalShell implements OnInit {
  protected readonly sesion = inject(SesionService);
  private readonly portal = inject(PortalService);

  protected readonly proveedor = signal<Proveedor | null>(null);
  protected readonly siglas = computed(() => iniciales(this.proveedor()?.razonSocial ?? this.sesion.usuario()?.nombre));

  ngOnInit(): void {
    this.portal.proveedor().subscribe({ next: p => this.proveedor.set(p), error: () => undefined });
  }
}
