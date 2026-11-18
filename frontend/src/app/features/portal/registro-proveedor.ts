import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Categoria, RegistroProveedorRespuesta } from '../../core/api/modelos';
import { mensajeError } from '../../core/http/api';
import { PortalService } from '../../core/servicios/portal.service';
import { PATRON_CLAVE } from '../../core/sesion/clave';

/** Formato del NIT que acepta el backend: solo números, con dígito de verificación opcional (900123456-7). */
const PATRON_NIT = /^\d{6,15}(-\d)?$/;
const PATRON_TELEFONO = /^\+?[0-9 -]{7,20}$/;

/**
 * Registro público de un proveedor. La empresa cliente le comparte un enlace con su código (`/registro/{código}`). El
 * proveedor llena sus datos y elige en qué categorías cotiza; queda PENDIENTE y no puede ingresar hasta que el jefe de
 * compras de esa empresa lo habilite.
 */
@Component({
  selector: 'sc-registro-proveedor',
  imports: [FormsModule, RouterLink],
  templateUrl: './registro-proveedor.html',
  styleUrl: './registro-proveedor.scss',
})
export class RegistroProveedor implements OnInit {
  private readonly portal = inject(PortalService);

  /** Código de ingreso de la empresa (de la ruta). */
  readonly empresa = input.required<string>();

  protected readonly categorias = signal<Categoria[] | null>(null);
  protected readonly empresaValida = signal(true);
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resultado = signal<RegistroProveedorRespuesta | null>(null);
  protected readonly verClave = signal(false);

  protected readonly razonSocial = signal('');
  protected readonly nit = signal('');
  protected readonly telefono = signal('');
  protected readonly direccion = signal('');
  protected readonly nombreContacto = signal('');
  protected readonly correo = signal('');
  protected readonly password = signal('');
  protected readonly confirmacion = signal('');
  protected readonly categoriaIds = signal<number[]>([]);

  protected readonly claveValida = computed(() => PATRON_CLAVE.test(this.password()));
  protected readonly coinciden = computed(() => this.password() === this.confirmacion());

  ngOnInit(): void {
    this.portal.categoriasDeRegistro(this.empresa()).subscribe({
      next: l => this.categorias.set(l),
      error: () => {
        this.categorias.set([]);
        this.empresaValida.set(false);
      },
    });
  }

  /** @param id categoría; la marca o la desmarca */
  protected alternar(id: number): void {
    this.categoriaIds.update(l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]));
  }

  protected registrar(): void {
    const problema = this.validar();
    if (problema) {
      this.error.set(problema);
      return;
    }
    this.enviando.set(true);
    this.error.set(null);
    this.portal.registrar(this.empresa(), {
      razonSocial: this.razonSocial().trim(), nit: this.nit().trim(), telefono: this.telefono().trim() || undefined,
      direccion: this.direccion().trim() || undefined, categoriaIds: this.categoriaIds(), nombreContacto: this.nombreContacto().trim(),
      correo: this.correo().trim(), password: this.password(),
    }).subscribe({
      next: r => {
        this.enviando.set(false);
        this.password.set('');
        this.confirmacion.set('');
        this.resultado.set(r);
      },
      error: e => {
        this.enviando.set(false);
        this.error.set(mensajeError(e));
      },
    });
  }

  /** Revisa lo mínimo; el backend vuelve a validar todo (NIT y correo únicos, categorías activas, contraseña). */
  private validar(): string | null {
    if (!this.razonSocial().trim()) {
      return 'Escribe la razón social de tu empresa.';
    }
    if (!PATRON_NIT.test(this.nit().trim())) {
      return 'El NIT lleva solo números, con dígito de verificación opcional (por ejemplo 900123456-7).';
    }
    if (this.telefono().trim() && !PATRON_TELEFONO.test(this.telefono().trim())) {
      return 'El teléfono no tiene un formato válido.';
    }
    if (!this.categoriaIds().length) {
      return 'Elige al menos una categoría en la que cotizas.';
    }
    if (!this.nombreContacto().trim()) {
      return 'Escribe el nombre de quien administrará el portal.';
    }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(this.correo().trim())) {
      return 'Escribe un correo válido: es con el que ingresarás.';
    }
    if (!this.claveValida()) {
      return 'La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.';
    }
    if (!this.coinciden()) {
      return 'Las dos contraseñas no coinciden.';
    }
    return null;
  }
}
