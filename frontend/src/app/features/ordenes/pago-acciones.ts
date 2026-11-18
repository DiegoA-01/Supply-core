import { Component, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Pago } from '../../core/api/modelos';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { Avisos, descargar } from '../../core/ui/avisos';

/** Extensión para el nombre del archivo según el tipo que devuelve el backend. */
function extension(tipo: string): string {
  return tipo.includes('pdf') ? '.pdf' : tipo.includes('png') ? '.png' : tipo.includes('jpeg') || tipo.includes('jpg') ? '.jpg' : '';
}

/**
 * Acciones sobre un pago ya registrado: ver o adjuntar el comprobante y anularlo con motivo. Supply-Core no mueve
 * dinero: solo registra. El pago anulado deja de sumar al pagado de la orden (lo recalcula el backend).
 */
@Component({
  selector: 'sc-pago-acciones',
  imports: [FormsModule],
  template: `
    <div class="fila acciones-pago">
      @if (pago().comprobanteDocumentoId) {
        <button type="button" class="boton chico" (click)="verComprobante()"><span class="material-symbols-outlined">attach_file</span> Ver comprobante</button>
      }
      @if (puedeEditar() && pago().estado === 'REGISTRADO' && pago().evidenciaPendiente) {
        <label class="boton chico subir" [class.deshabilitado]="trabajando()">
          <span class="material-symbols-outlined">upload</span> Adjuntar comprobante
          <input type="file" accept="application/pdf,image/*" [disabled]="trabajando()" (change)="adjuntar($event)" [attr.aria-label]="'Adjuntar comprobante del pago ' + pago().id">
        </label>
      }
      @if (puedeEditar() && pago().estado === 'REGISTRADO' && !anulando()) {
        <button type="button" class="boton chico peligro" (click)="anulando.set(true)">Anular</button>
      }
    </div>
    @if (anulando()) {
      <div class="anular">
        <label [for]="'motivo-' + pago().id">Motivo de la anulación (obligatorio, mínimo 5 caracteres)</label>
        <input [id]="'motivo-' + pago().id" class="entrada" maxlength="500" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"
               placeholder="Ej. Se registró dos veces el mismo pago">
        <div class="fila">
          <button type="button" class="boton chico peligro" [disabled]="trabajando()" (click)="anular()">Anular pago</button>
          <button type="button" class="boton chico" (click)="cancelar()">Cancelar</button>
        </div>
      </div>
    }
  `,
  styles: `
    .acciones-pago { gap: 8px; flex-wrap: wrap; margin-top: 6px; }
    .subir { cursor: pointer; position: relative; overflow: hidden; }
    .subir input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }
    .deshabilitado { opacity: .6; pointer-events: none; }
    .anular { display: flex; flex-direction: column; gap: 8px; margin-top: 8px; padding: 10px; border-radius: 10px; background: #fff3f0; }
    .anular label { font-size: 13px; font-weight: 600; }
  `,
})
export class PagoAcciones {
  private readonly servicio = inject(OrdenesService);
  private readonly avisos = inject(Avisos);

  /** Pago registrado. */
  readonly pago = input.required<Pago>();
  /** Si quien mira puede adjuntar y anular (tesorería, sin suscripción en mora). */
  readonly puedeEditar = input(false);
  /** Avisa que el pago cambió (la pantalla recarga sus totales). */
  readonly cambio = output<void>();

  protected readonly anulando = signal(false);
  protected readonly motivo = signal('');
  protected readonly trabajando = signal(false);

  protected verComprobante(): void {
    const id = this.pago().comprobanteDocumentoId;
    if (!id) {
      return;
    }
    this.servicio.comprobante(id).subscribe({
      next: b => descargar(b, `comprobante-pago-${this.pago().id}${extension(b.type)}`),
      error: e => this.avisos.error(e),
    });
  }

  /** @param evento cambio del selector de archivo */
  protected adjuntar(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (!archivo) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.adjuntarComprobante(this.pago().id!, archivo).subscribe({
      next: () => {
        this.trabajando.set(false);
        entrada.value = '';
        this.avisos.ok('Comprobante adjuntado.');
        this.cambio.emit();
      },
      error: e => {
        this.trabajando.set(false);
        entrada.value = '';
        this.avisos.error(e);
      },
    });
  }

  protected anular(): void {
    const motivo = this.motivo().trim();
    if (motivo.length < 5) {
      this.avisos.ok('Escribe el motivo: mínimo 5 caracteres.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.anularPago(this.pago().id!, motivo).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.cancelar();
        this.avisos.ok('Pago anulado. Ya no suma al pagado de la orden.');
        this.cambio.emit();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected cancelar(): void {
    this.anulando.set(false);
    this.motivo.set('');
  }
}
