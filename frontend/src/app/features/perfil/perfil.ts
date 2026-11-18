import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ConfiguracionMfa, Usuario } from '../../core/api/modelos';
import { nombreRol } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { Avisos } from '../../core/ui/avisos';
import { PATRON_CLAVE } from '../../core/sesion/clave';
import { SesionService } from '../../core/sesion/sesion.service';

/** Mi perfil: datos del usuario de la sesión y cambio de su propia contraseña. */
@Component({
  selector: 'sc-perfil',
  imports: [FormsModule, RouterLink, FechaPipe],
  templateUrl: './perfil.html',
  styleUrl: './perfil.scss',
})
export class Perfil implements OnInit {
  private readonly usuarios = inject(UsuariosService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly perfil = signal<Usuario | null>(null);
  protected readonly actual = signal('');
  protected readonly nueva = signal('');
  protected readonly confirmacion = signal('');
  protected readonly verClave = signal(false);
  protected readonly trabajando = signal(false);

  protected readonly claveValida = computed(() => PATRON_CLAVE.test(this.nueva()));
  protected readonly coinciden = computed(() => this.nueva() === this.confirmacion());

  /** Secreto para vincular la app autenticadora mientras se configura; {@code null} fuera de ese paso. */
  protected readonly mfa = signal<ConfiguracionMfa | null>(null);
  protected readonly codigoMfa = signal('');

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.usuarios.perfil().subscribe({ next: p => this.perfil.set(p), error: e => this.avisos.error(e) });
  }

  protected configurarMfa(): void {
    this.trabajando.set(true);
    this.sesion.configurarMfa().subscribe({
      next: m => {
        this.trabajando.set(false);
        this.codigoMfa.set('');
        this.mfa.set(m);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected activarMfa(): void {
    if (!/^\d{6}$/.test(this.codigoMfa().trim())) {
      this.avisos.ok('Escribe los 6 dígitos que muestra tu app.');
      return;
    }
    this.trabajando.set(true);
    this.sesion.activarMfa(this.codigoMfa()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.mfa.set(null);
        this.avisos.ok('Segundo factor activado: desde ahora te pedimos el código al ingresar.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param rol código del rol */
  protected rol(rol: string): string {
    return nombreRol(rol);
  }

  protected cambiar(): void {
    if (!this.actual() || !this.claveValida() || !this.coinciden()) {
      this.avisos.ok('Revisa los campos: la nueva debe cumplir las reglas y coincidir en los dos campos.');
      return;
    }
    if (this.actual() === this.nueva()) {
      this.avisos.ok('La contraseña nueva debe ser distinta de la actual.');
      return;
    }
    this.trabajando.set(true);
    this.usuarios.cambiarPassword(this.actual(), this.nueva()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.actual.set('');
        this.nueva.set('');
        this.confirmacion.set('');
        this.avisos.ok('Contraseña cambiada. Úsala la próxima vez que ingreses.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
