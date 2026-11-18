import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { mensajeError } from '../../core/http/api';
import { PATRON_CLAVE } from '../../core/sesion/clave';
import { SesionService } from '../../core/sesion/sesion.service';

/**
 * Recuperación de una contraseña olvidada, en dos pasos y sin revelar si la cuenta existe: 1) código de empresa y correo,
 * 2) el código que llegó por correo y la contraseña nueva. El código sirve una vez y cierra las sesiones abiertas.
 */
@Component({
  selector: 'sc-recuperar',
  imports: [FormsModule, RouterLink],
  templateUrl: './recuperar.html',
  styleUrl: './recuperar.scss',
})
export class Recuperar {
  private readonly sesion = inject(SesionService);

  protected readonly paso = signal<'pedir' | 'cambiar' | 'listo'>('pedir');
  protected readonly empresa = signal(this.sesion.ultimaEmpresa());
  protected readonly correo = signal('');
  protected readonly token = signal('');
  protected readonly nueva = signal('');
  protected readonly confirmacion = signal('');
  protected readonly verClave = signal(false);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly aviso = signal<string | null>(null);

  protected readonly claveValida = computed(() => PATRON_CLAVE.test(this.nueva()));
  protected readonly coinciden = computed(() => this.nueva() === this.confirmacion());

  protected pedir(): void {
    if (!this.empresa().trim() || !this.correo().trim()) {
      this.error.set('Escribe el código de tu empresa y tu correo.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.sesion.recuperar(this.empresa(), this.correo()).subscribe({
      next: r => {
        this.enviando.set(false);
        this.aviso.set(r.mensaje ?? null);
        this.paso.set('cambiar');
      },
      error: e => this.fallo(e),
    });
  }

  protected cambiar(): void {
    if (!this.token().trim()) {
      this.error.set('Pega el código que llegó a tu correo.');
      return;
    }
    if (!this.claveValida() || !this.coinciden()) {
      this.error.set('La contraseña nueva debe cumplir las reglas y coincidir en los dos campos.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.sesion.restablecer(this.empresa(), this.token(), this.nueva()).subscribe({
      next: () => {
        this.enviando.set(false);
        this.nueva.set('');
        this.confirmacion.set('');
        this.paso.set('listo');
      },
      error: e => this.fallo(e),
    });
  }

  private fallo(e: unknown): void {
    this.enviando.set(false);
    this.error.set(mensajeError(e));
  }
}
