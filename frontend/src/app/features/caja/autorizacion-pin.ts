import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AccionAutorizada } from '../../core/api/modelos';
import { CajaService } from '../../core/servicios/caja.service';
import { Avisos } from '../../core/ui/avisos';

/** Texto de cada acción que se autoriza. */
const ACCIONES: Record<AccionAutorizada, string> = {
  ANULAR_VENTA: 'anular la venta', DESCUENTO: 'el descuento', PRECIO_BAJO_MINIMO: 'vender bajo el precio mínimo',
  DEVOLUCION: 'la devolución', RETIRO: 'el retiro de efectivo', GASTO: 'el gasto', INGRESO: 'el ingreso de efectivo',
};

/**
 * Autorización en el mostrador (spec 003): el supervisor escribe su correo y su PIN en la pantalla del cajero, sin
 * cerrar la sesión del cajero. Devuelve un permiso de un solo uso; el PIN no se guarda en ningún lado y se borra del
 * campo apenas se envía. Lo usan el POS (005) y la anulación y devolución (006).
 */
@Component({
  selector: 'sc-autorizacion-pin',
  imports: [FormsModule],
  template: `
    <form class="autorizacion" (ngSubmit)="enviar()" autocomplete="off">
      <p class="sin-margen"><span class="material-symbols-outlined">lock</span>
        Un supervisor debe autorizar {{ texto() }}. Nadie autoriza lo suyo.</p>
      <div class="campos">
        <div class="campo"><label for="ap-correo">Correo del supervisor</label>
          <input id="ap-correo" name="correo" type="email" class="entrada" autocomplete="off" [ngModel]="correo()" (ngModelChange)="correo.set($event)"></div>
        <div class="campo"><label for="ap-pin">PIN</label>
          <input id="ap-pin" name="pin" type="password" class="entrada" inputmode="numeric" maxlength="6" autocomplete="off"
            [ngModel]="pin()" (ngModelChange)="pin.set($event)"></div>
      </div>
      <div class="fila">
        <button type="submit" class="boton primario" [disabled]="enviando() || !valido()">Autorizar</button>
        <button type="button" class="boton" (click)="cancelado.emit()">Cancelar</button>
      </div>
    </form>
  `,
  styles: `
    .autorizacion { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--sc-borde); border-radius: 12px; background: #fffaf3; }
    .autorizacion p { display: flex; gap: 6px; align-items: center; font-size: 13px; }
    .campos { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 10px; }
  `,
})
export class AutorizacionPin {
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);

  readonly accion = input.required<AccionAutorizada>();
  readonly turnoId = input.required<number>();
  readonly referenciaId = input<number | null>(null);
  /** Permiso de un solo uso y nombre de quien autorizó. */
  readonly autorizado = output<{ token: string; supervisor: string }>();
  readonly cancelado = output<void>();

  protected readonly correo = signal('');
  protected readonly pin = signal('');
  protected readonly enviando = signal(false);

  protected texto(): string {
    return ACCIONES[this.accion()];
  }

  protected valido(): boolean {
    return this.correo().trim().length > 3 && /^\d{6}$/.test(this.pin());
  }

  protected enviar(): void {
    if (!this.valido() || this.enviando()) {
      return;
    }
    const pin = this.pin();
    this.pin.set('');
    this.enviando.set(true);
    this.caja.autorizar({ correo: this.correo().trim(), pin, accion: this.accion(), turnoId: this.turnoId(), referenciaId: this.referenciaId() })
      .subscribe({
        next: r => {
          this.enviando.set(false);
          this.autorizado.emit({ token: r.token!, supervisor: r.supervisor ?? '' });
        },
        error: e => {
          this.enviando.set(false);
          this.avisos.error(e);
        },
      });
  }
}
