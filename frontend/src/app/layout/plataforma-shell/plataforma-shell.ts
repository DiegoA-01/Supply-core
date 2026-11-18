import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { iniciales } from '../../core/formato/formato';
import { PlataformaSesionService } from '../../core/sesion/plataforma-sesion.service';

/** Marco de la consola del dueño. Reutiliza el aspecto del marco de la empresa con su propio menú. */
@Component({
  selector: 'sc-plataforma-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="marco">
      <aside class="lateral" aria-label="Menú de la plataforma">
        <a class="marca" routerLink="/plataforma/empresas">
          <span class="escudo material-symbols-outlined">shield</span><span>Plataforma</span>
        </a>
        <div class="empresa"><strong>Dueño del software</strong><span>Supply-Core · acceso con MFA</span></div>
        <p class="seccion">Clientes</p>
        <a class="opcion" routerLink="/plataforma/empresas" routerLinkActive="activa">
          <span class="material-symbols-outlined">apartment</span><span>Empresas</span>
        </a>
        <a class="opcion" routerLink="/plataforma/planes" routerLinkActive="activa">
          <span class="material-symbols-outlined">inventory_2</span><span>Planes y módulos</span>
        </a>
        <p class="seccion">Operación</p>
        <a class="opcion" routerLink="/plataforma/auditoria" routerLinkActive="activa">
          <span class="material-symbols-outlined">shield</span><span>Auditoría global</span>
        </a>
        <a class="opcion pie" routerLink="/ingreso">
          <span class="material-symbols-outlined">home</span><span>Volver a una empresa</span>
        </a>
      </aside>
      <div class="contenido">
        <header class="superior">
          <span class="chip verde"><span class="material-symbols-outlined">check</span>MFA activo</span>
          <span class="avatar">{{ siglas() }}</span>
          <span class="datos"><strong>{{ sesion.nombre() }}</strong><span>Dueño de la plataforma</span></span>
          <button type="button" class="boton chico" (click)="sesion.salir()">Salir</button>
        </header>
        <main><router-outlet /></main>
      </div>
    </div>
  `,
  styles: `
    .marco { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 20px; min-height: 100vh; padding: 16px;
      background: radial-gradient(900px 500px at 0% 0%, #f6c8b7 0%, transparent 60%), linear-gradient(180deg, #fbf4f1, #ececec 45%); }
    .lateral { position: sticky; top: 16px; height: calc(100vh - 32px); display: flex; flex-direction: column; gap: 4px; padding: 20px 16px;
      border-radius: 28px; color: #fff; background: linear-gradient(180deg, #7a645c 0%, #4c4c4c 30%, #484848 75%, #6b4b41 100%); }
    .marca { display: flex; align-items: center; gap: 10px; color: #fff; text-decoration: none; font-weight: 700; font-size: 19px; padding: 4px 8px 12px; }
    .escudo { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--sc-primario); font-size: 20px; }
    .empresa { display: flex; flex-direction: column; padding: 12px 14px; border-radius: 12px; border: 1px solid rgb(255 255 255 / 18%); background: rgb(255 255 255 / 8%);
      strong { font-size: 14px; } span { font-size: 12px; color: rgb(255 255 255 / 65%); } }
    .seccion { margin: 14px 10px 6px; font-size: 11px; letter-spacing: .12em; text-transform: uppercase; color: rgb(255 255 255 / 45%); }
    .opcion { display: flex; align-items: center; gap: 12px; min-height: 42px; padding: 0 12px; border-radius: 10px; color: rgb(255 255 255 / 85%); text-decoration: none;
      &.activa { color: #fff; background: linear-gradient(180deg, #f06a3c, #e04a17); }
      &.pie { margin-top: auto; } }
    .contenido { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
    .superior { display: flex; align-items: center; justify-content: flex-end; gap: 12px; padding: 12px 16px; border-radius: 20px; background: rgb(255 255 255 / 80%); }
    .datos { display: flex; flex-direction: column; font-size: 13px; color: var(--sc-texto-2); strong { font-size: 15px; color: var(--sc-texto); } }
    .avatar { display: grid; place-items: center; width: 40px; height: 40px; border-radius: 50%; background: #eee; font-weight: 600; font-size: 14px; }
    main { padding: 4px 8px 32px; }
    @media (max-width: 900px) { .marco { grid-template-columns: 1fr; } .lateral { position: static; height: auto; } .opcion.pie { margin-top: 8px; } }
  `,
})
export class PlataformaShell {
  protected readonly sesion = inject(PlataformaSesionService);
  protected readonly siglas = computed(() => iniciales(this.sesion.nombre()));
}
