import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { DesafioMfa, SesionPlataforma } from '../api/modelos';
import { api } from '../http/api';

/**
 * Sesión del dueño de la plataforma: contraseña + código MFA, con un JWT propio que no sirve en la API de una
 * empresa (ni el de una empresa sirve aquí). No tiene refresh: al vencer se vuelve a ingresar.
 */
@Injectable({ providedIn: 'root' })
export class PlataformaSesionService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly sesion = signal<SesionPlataforma | null>(null);

  readonly nombre = computed(() => this.sesion()?.nombre ?? '');
  readonly autenticado = computed(() => this.sesion() !== null);

  /** JWT de plataforma vigente, o {@code null}. */
  tokenActual(): string | null {
    return this.sesion()?.token ?? null;
  }

  /**
   * Primer paso: correo y contraseña. Devuelve el desafío para el código MFA (y el secreto si aún no se configuró).
   *
   * @param correo   correo del dueño
   * @param password contraseña
   */
  ingresar(correo: string, password: string): Observable<DesafioMfa> {
    return this.http.post<DesafioMfa>(api('/api/plataforma/auth/login'), { correo: correo.trim(), password });
  }

  /**
   * Segundo paso: el código de la app autenticadora.
   *
   * @param desafio desafío recibido en el primer paso
   * @param codigo  código de 6 dígitos
   */
  confirmar(desafio: string, codigo: string): Observable<SesionPlataforma> {
    return this.http.post<SesionPlataforma>(api('/api/plataforma/auth/mfa'), { desafio, codigo: codigo.trim() })
      .pipe(tap(s => this.sesion.set(s)));
  }

  /** Cierra la sesión del dueño. */
  salir(): void {
    this.sesion.set(null);
    void this.router.navigateByUrl('/plataforma/ingreso');
  }
}
