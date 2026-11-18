import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { DesafioMfa } from '../../core/api/modelos';
import { mensajeError } from '../../core/http/api';
import { PlataformaSesionService } from '../../core/sesion/plataforma-sesion.service';

/** Ingreso del dueño de la plataforma: contraseña y luego el código de la app autenticadora (MFA obligatorio). */
@Component({
  selector: 'sc-ingreso-plataforma',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pantalla">
      <form class="tarjeta caja" (ngSubmit)="desafio() ? confirmar() : ingresar()">
        <div class="marca"><span class="escudo material-symbols-outlined">shield</span><strong>Consola del dueño</strong></div>
        @if (!desafio()) {
          <h1>Ingresa a la plataforma</h1>
          <p class="texto-2">Desde aquí activas empresas, planes y módulos. No ves los datos de compra de ninguna empresa.</p>
          <div class="campo"><label for="correo">Correo</label>
            <input id="correo" class="entrada" type="email" name="correo" autocomplete="username" [(ngModel)]="correo" required></div>
          <div class="campo"><label for="clave">Contraseña</label>
            <div class="con-ojo">
              <input id="clave" class="entrada" [type]="verClave() ? 'text' : 'password'" name="clave" autocomplete="current-password" [(ngModel)]="clave" required>
              <button type="button" class="ojo" (click)="verClave.set(!verClave())"
                      [attr.aria-label]="verClave() ? 'Ocultar contraseña' : 'Mostrar contraseña'" [attr.aria-pressed]="verClave()">
                <span class="material-symbols-outlined">{{ verClave() ? 'visibility_off' : 'visibility' }}</span>
              </button>
            </div></div>
        } @else {
          <h1>Código de verificación</h1>
          @if (desafio()!.mfaConfigurado === false) {
            <div class="aviso ambar">
              <div><strong>Configura tu app autenticadora (primera vez)</strong>
                Agrega esta clave en Google Authenticator, Microsoft Authenticator o similar:
                <code>{{ desafio()!.secretoMfa }}</code></div>
            </div>
          }
          <p class="texto-2">Escribe el código de 6 dígitos que muestra tu app.</p>
          <div class="campo"><label for="codigo">Código</label>
            <input id="codigo" class="entrada codigo" name="codigo" inputmode="numeric" autocomplete="one-time-code" maxlength="6" [(ngModel)]="codigo" required></div>
        }
        @if (error()) { <div class="aviso rojo" role="alert">{{ error() }}</div> }
        <button type="submit" class="boton primario bloque" [disabled]="enviando()">{{ desafio() ? 'Verificar' : 'Continuar' }}</button>
        <a routerLink="/ingreso" class="texto-chico volver">Volver al ingreso de empresas</a>
      </form>
    </div>
  `,
  styles: `
    .pantalla { min-height: 100vh; display: grid; place-items: center; padding: 20px; background: #0a0a0a; }
    .caja { width: min(440px, 100%); display: flex; flex-direction: column; gap: 16px; padding: 32px; }
    .marca { display: flex; align-items: center; gap: 10px; }
    .escudo { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 8px; background: var(--sc-primario); color: #fff; }
    h1 { font-size: 24px; margin: 0; }
    p { margin: 0; }
    code { display: block; margin-top: 6px; font-size: 15px; word-break: break-all; }
    .codigo { font-size: 22px; letter-spacing: .3em; text-align: center; }
    .volver { text-align: center; }
  `,
})
export class IngresoPlataforma {
  private readonly sesion = inject(PlataformaSesionService);
  private readonly router = inject(Router);

  protected correo = '';
  protected clave = '';
  protected codigo = '';
  protected readonly desafio = signal<DesafioMfa | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly enviando = signal(false);
  /** Muestra la contraseña para revisar que esté bien escrita. */
  protected readonly verClave = signal(false);

  protected ingresar(): void {
    this.enviar(() => this.sesion.ingresar(this.correo, this.clave).subscribe({
      next: d => {
        this.desafio.set(d);
        this.enviando.set(false);
      },
      error: e => this.fallo(e),
    }));
  }

  protected confirmar(): void {
    this.enviar(() => this.sesion.confirmar(this.desafio()!.desafio!, this.codigo).subscribe({
      next: () => void this.router.navigateByUrl('/plataforma/empresas'),
      error: e => this.fallo(e),
    }));
  }

  private enviar(accion: () => void): void {
    this.enviando.set(true);
    this.error.set(null);
    accion();
  }

  private fallo(e: unknown): void {
    this.enviando.set(false);
    this.error.set(mensajeError(e));
  }
}
