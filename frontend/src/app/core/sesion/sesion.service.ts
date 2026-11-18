import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, forkJoin, of, shareReplay, tap, throwError } from 'rxjs';

import { ConfiguracionMfa, Empresa, LoginResponse, MensajeApi, ModulosEmpresa, UsuarioSesion } from '../api/modelos';
import { api } from '../http/api';

const CLAVE_REFRESH = 'sc.refresh';
const CLAVE_EMPRESA = 'sc.empresa';

/**
 * Sesión de un usuario de empresa (internos y proveedores).
 * <p>El JWT de 15 minutos vive solo en memoria; el refresh token de un solo uso va en {@code sessionStorage}, así
 * que al cerrar la pestaña la sesión termina. Todo el manejo de la sesión está aquí: pasar el refresh a una cookie
 * {@code HttpOnly} (fase 3 de seguridad) solo cambia este archivo.</p>
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private token: string | null = null;
  private refrescando: Observable<LoginResponse> | null = null;

  /** Usuario de la sesión ({@code null} sin sesión). */
  readonly usuario = signal<UsuarioSesion | null>(null);
  /** Empresa del usuario interno (encabezado). */
  readonly empresa = signal<Empresa | null>(null);
  /** Plan, estado de la suscripción y módulos activos de la empresa. */
  readonly modulos = signal<ModulosEmpresa | null>(null);

  readonly autenticado = computed(() => this.usuario() !== null);
  readonly esProveedor = computed(() => this.usuario()?.roles?.includes('PROVEEDOR') ?? false);
  /** Suscripción morosa: el backend solo deja leer. */
  readonly soloLectura = computed(() => this.modulos()?.estado === 'MOROSA');

  /** JWT vigente, o {@code null}. */
  tokenActual(): string | null {
    return this.token;
  }

  /**
   * Inicia sesión con el código de la empresa. Si el usuario tiene MFA (o es administrador y debe configurarlo), la
   * respuesta trae {@code desafioMfa} y todavía no hay sesión: falta {@link #confirmarMfa}.
   *
   * @param empresa  código público de la empresa
   * @param correo   correo del usuario
   * @param password contraseña
   */
  ingresar(empresa: string, correo: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(api('/api/auth/login'), { empresa: empresa.trim(), correo: correo.trim(), password })
      .pipe(tap(r => {
        if (r.token) {
          this.guardar(r);
        }
        this.escribir(localStorage, CLAVE_EMPRESA, empresa.trim());
      }));
  }

  /**
   * Segundo paso del ingreso: el código de 6 dígitos de la app autenticadora.
   *
   * @param desafio desafío recibido en {@link #ingresar}
   * @param codigo  código de 6 dígitos
   */
  confirmarMfa(desafio: string, codigo: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(api('/api/auth/mfa'), { desafio, codigo: codigo.trim() }).pipe(tap(r => this.guardar(r)));
  }

  /** Pide el secreto para vincular la app autenticadora del usuario de la sesión (MFA voluntario). */
  configurarMfa(): Observable<ConfiguracionMfa> {
    return this.http.post<ConfiguracionMfa>(api('/api/auth/mfa/configurar'), {});
  }

  /** @param codigo código de 6 dígitos con el que se confirma que la app quedó vinculada */
  activarMfa(codigo: string): Observable<void> {
    return this.http.post<void>(api('/api/auth/mfa/activar'), { codigo: codigo.trim() });
  }

  /**
   * Pide el código de recuperación por correo. El aviso es el mismo exista o no la cuenta.
   *
   * @param empresa código de la empresa
   * @param correo  correo de la cuenta
   */
  recuperar(empresa: string, correo: string): Observable<MensajeApi> {
    return this.http.post<MensajeApi>(api('/api/auth/recuperar'), { empresa: empresa.trim(), correo: correo.trim() });
  }

  /**
   * Cambia la contraseña con el código recibido por correo (sirve una sola vez y cierra las sesiones abiertas).
   *
   * @param empresa       código de la empresa
   * @param token         código recibido por correo
   * @param passwordNueva contraseña nueva
   */
  restablecer(empresa: string, token: string, passwordNueva: string): Observable<MensajeApi> {
    return this.http.post<MensajeApi>(api('/api/auth/restablecer'), { empresa: empresa.trim(), token: token.trim(), passwordNueva });
  }

  /** Último código de empresa usado en este navegador, para no escribirlo cada vez. */
  ultimaEmpresa(): string {
    return this.leer(localStorage, CLAVE_EMPRESA) ?? '';
  }

  /**
   * Canjea el refresh token por un JWT nuevo. Si varias peticiones vencen a la vez, comparten un solo canje
   * (el refresh sirve una vez: un segundo canje revocaría toda la sesión).
   */
  refrescar(): Observable<LoginResponse> {
    if (this.refrescando) {
      return this.refrescando;
    }
    const refreshToken = this.leer(sessionStorage, CLAVE_REFRESH);
    if (!refreshToken) {
      return throwError(() => new Error('Sin sesión renovable'));
    }
    this.refrescando = this.http.post<LoginResponse>(api('/api/auth/refresh'), { refreshToken }).pipe(
      tap(r => this.guardar(r)),
      finalize(() => (this.refrescando = null)),
      shareReplay(1),
    );
    return this.refrescando;
  }

  /** Recupera la sesión al recargar la página (si hay refresh token) y carga los datos de la empresa. */
  async restaurar(): Promise<void> {
    if (!this.leer(sessionStorage, CLAVE_REFRESH)) {
      return;
    }
    try {
      await firstValueFrom(this.refrescar());
      await firstValueFrom(this.cargarContexto());
    } catch {
      this.limpiar();
    }
  }

  /** Carga la empresa y sus módulos para el encabezado y el menú (no aplica al proveedor). */
  cargarContexto(): Observable<unknown> {
    if (this.esProveedor()) {
      return of(null);
    }
    return forkJoin({
      empresa: this.http.get<Empresa>(api('/api/empresa')).pipe(catchError(() => of(null))),
      modulos: this.http.get<ModulosEmpresa>(api('/api/empresa/modulos')).pipe(catchError(() => of(null))),
    }).pipe(tap(r => {
      this.empresa.set(r.empresa);
      this.modulos.set(r.modulos);
    }));
  }

  /**
   * Indica si el usuario tiene alguno de los roles.
   *
   * @param roles roles aceptados (vacío = cualquier usuario)
   */
  tieneRol(roles: readonly string[] | undefined): boolean {
    if (!roles?.length) {
      return true;
    }
    const propios = this.usuario()?.roles ?? [];
    return roles.some(r => propios.includes(r));
  }

  /**
   * Indica si la empresa tiene el módulo activo. Sin datos de módulos se deja pasar y decide el backend (403).
   *
   * @param modulo código del módulo ({@code undefined} = no depende de un módulo)
   */
  tieneModulo(modulo: string | undefined): boolean {
    const m = this.modulos();
    return !modulo || !m?.modulos ? true : m.modulos.includes(modulo);
  }

  /** Cierra la sesión en el servidor y en el navegador. */
  salir(): void {
    const refreshToken = this.leer(sessionStorage, CLAVE_REFRESH);
    if (refreshToken && this.token) {
      this.http.post(api('/api/auth/logout'), { refreshToken }).subscribe({ error: () => undefined });
    }
    this.limpiar();
    void this.router.navigateByUrl('/ingreso');
  }

  /** Cierra la sesión en todos los dispositivos del usuario. */
  salirDeTodo(): void {
    this.http.post(api('/api/auth/logout-todo'), {}).subscribe({ error: () => undefined, complete: () => this.salir() });
  }

  /** La sesión venció y no se pudo renovar: vuelve al ingreso avisando por qué. */
  expirada(): void {
    this.limpiar();
    void this.router.navigate(['/ingreso'], { queryParams: { motivo: 'sesion' } });
  }

  private guardar(r: LoginResponse): void {
    this.token = r.token ?? null;
    this.usuario.set(r.usuario ?? null);
    if (r.refreshToken) {
      this.escribir(sessionStorage, CLAVE_REFRESH, r.refreshToken);
    }
  }

  private limpiar(): void {
    this.token = null;
    this.usuario.set(null);
    this.empresa.set(null);
    this.modulos.set(null);
    try {
      sessionStorage.removeItem(CLAVE_REFRESH);
    } catch {
      // almacenamiento bloqueado: no hay nada que borrar
    }
  }

  private leer(almacen: Storage, clave: string): string | null {
    try {
      return almacen.getItem(clave);
    } catch {
      return null;
    }
  }

  private escribir(almacen: Storage, clave: string, valor: string): void {
    try {
      almacen.setItem(clave, valor);
    } catch {
      // modo privado o almacenamiento bloqueado: la sesión sigue en memoria
    }
  }
}
