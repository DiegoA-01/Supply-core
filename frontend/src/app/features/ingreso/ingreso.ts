import { Component, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';


import { LoginResponse } from '../../core/api/modelos';
import { mensajeError } from '../../core/http/api';
import { SesionService } from '../../core/sesion/sesion.service';

/** Vista 1 · Ingreso por empresa (UC-01). Internos y proveedores entran por aquí; el rol decide a dónde van. */
@Component({
  selector: 'sc-ingreso',
  imports: [FormsModule, ReactiveFormsModule, RouterLink],
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
  /** Desafío del segundo paso (código de la app autenticadora); {@code null} mientras se pide la contraseña. */
  protected readonly desafio = signal<LoginResponse | null>(null);
  protected readonly codigo = signal('');
  /** Muestra la contraseña para revisar que esté bien escrita. */
  protected readonly verClave = signal(false);

  /** Lleva al registro de proveedores de la empresa cuyo código está escrito en el formulario. */
  protected registrarProveedor(): void {
    const codigo = this.form.controls.empresa.value.trim();
    if (!codigo) {
      this.error.set('Escribe primero el código de la empresa a la que le vas a cotizar.');
      return;
    }
    void this.router.navigate(['/registro', codigo]);
  }

  protected ingresar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { empresa, correo, password } = this.form.getRawValue();
    this.enviando.set(true);
    this.error.set(null);
    this.sesion.ingresar(empresa, correo, password).subscribe({
      next: r => {
        if (r.desafioMfa) {
          this.desafio.set(r);
          this.enviando.set(false);
          return;
        }
        this.entrar();
      },
      error: e => this.fallo(e),
    });
  }

  /** Segundo paso: confirma el código de 6 dígitos de la app autenticadora. */
  protected confirmar(): void {
    const d = this.desafio();
    if (!d?.desafioMfa || !/^\d{6}$/.test(this.codigo().trim())) {
      this.error.set('Escribe los 6 dígitos que muestra tu app.');
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.sesion.confirmarMfa(d.desafioMfa, this.codigo()).subscribe({ next: () => this.entrar(), error: e => this.fallo(e) });
  }

  /** Vuelve a pedir la contraseña (por ejemplo, si el desafío venció). */
  protected volver(): void {
    this.desafio.set(null);
    this.codigo.set('');
    this.error.set(null);
  }

  private entrar(): void {
    this.sesion.cargarContexto().subscribe({
      next: () => void this.router.navigateByUrl(this.sesion.esProveedor() ? '/portal' : '/app/inicio'),
      error: e => this.fallo(e),
    });
  }

  private fallo(e: unknown): void {
    // El backend responde igual para clave errada, usuario inexistente o cuenta bloqueada (no revela cuál).
    this.error.set(mensajeError(e));
    this.enviando.set(false);
  }

  /** @param campo nombre del control */
  protected invalido(campo: 'empresa' | 'correo' | 'password'): boolean {
    const c = this.form.controls[campo];
    return c.invalid && c.touched;
  }
}
