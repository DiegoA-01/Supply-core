import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { mensajeError } from '../../core/http/api';
import { SesionService } from '../../core/sesion/sesion.service';

/** Vista 1 · Ingreso por empresa (UC-01). Internos y proveedores entran por aquí; el rol decide a dónde van. */
@Component({
  selector: 'sc-ingreso',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './ingreso.html',
  styleUrl: './ingreso.scss',
})
export class Ingreso {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    empresa: [this.sesion.ultimaEmpresa(), [Validators.required, Validators.maxLength(40)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: ['', [Validators.required, Validators.maxLength(72)]],
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(
    this.ruta.snapshot.queryParamMap.get('motivo') === 'sesion' ? 'Tu sesión terminó. Ingresa de nuevo.' : null);
  protected readonly olvido = signal(false);
  /** Muestra la contraseña para revisar que esté bien escrita. */
  protected readonly verClave = signal(false);

  protected ingresar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { empresa, correo, password } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.sesion.ingresar(empresa, correo, password).pipe(switchMap(() => this.sesion.cargarContexto())).subscribe({
      next: () => void this.router.navigateByUrl(this.sesion.esProveedor() ? '/portal' : '/app/inicio'),
      error: e => {
        // El backend responde igual para clave errada, usuario inexistente o cuenta bloqueada (no revela cuál).
        this.error.set(mensajeError(e));
        this.enviando.set(false);
      },
    });
  }

  /** @param campo nombre del control */
  protected invalido(campo: 'empresa' | 'correo' | 'password'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }
}
