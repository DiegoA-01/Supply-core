import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Pagina, Rol, Usuario, UsuarioCambios, UsuarioNuevo } from '../api/modelos';
import { api, parametros } from '../http/api';

/** Usuarios de la empresa (los maneja su administrador) y el perfil propio. */
@Injectable({ providedIn: 'root' })
export class UsuariosService {
  private readonly http = inject(HttpClient);

  /** @param filtros texto (nombre o correo), estado y rol */
  buscar(filtros: { texto?: string | null; activo?: boolean | null; rol?: string | null }): Observable<Pagina<Usuario>> {
    return this.http.get<Pagina<Usuario>>(api('/api/usuarios'), {
      params: parametros({ texto: filtros.texto, activo: filtros.activo, rol: filtros.rol, size: 100, sort: 'nombreCompleto,asc' }),
    });
  }

  /** Catálogo de roles que el administrador puede asignar. */
  roles(): Observable<Rol[]> {
    return this.http.get<Rol[]>(api('/api/roles'));
  }

  /** @param usuario datos del usuario nuevo con su contraseña inicial */
  crear(usuario: UsuarioNuevo): Observable<Usuario> {
    return this.http.post<Usuario>(api('/api/usuarios'), usuario);
  }

  /** @param id usuario; @param cambios nombre, correo, roles y centro de costo */
  actualizar(id: number, cambios: UsuarioCambios): Observable<Usuario> {
    return this.http.put<Usuario>(api(`/api/usuarios/${id}`), cambios);
  }

  /** @param id usuario; @param activo nuevo estado (desactivar no borra su historial) */
  cambiarEstado(id: number, activo: boolean): Observable<Usuario> {
    return this.http.patch<Usuario>(api(`/api/usuarios/${id}/${activo ? 'activar' : 'desactivar'}`), {});
  }

  /** @param id usuario; el administrador borra su vínculo con la app autenticadora (la vuelve a configurar al ingresar) */
  reiniciarMfa(id: number): Observable<void> {
    return this.http.patch<void>(api(`/api/usuarios/${id}/mfa/reiniciar`), {});
  }

  /** @param id usuario; @param passwordNueva contraseña que el administrador le entrega */
  restablecerPassword(id: number, passwordNueva: string): Observable<void> {
    return this.http.put<void>(api(`/api/usuarios/${id}/password`), { passwordNueva });
  }

  /** Datos del usuario de la sesión. */
  perfil(): Observable<Usuario> {
    return this.http.get<Usuario>(api('/api/perfil'));
  }

  /** @param passwordActual la que usa hoy; @param passwordNueva la nueva */
  cambiarPassword(passwordActual: string, passwordNueva: string): Observable<void> {
    return this.http.put<void>(api('/api/perfil/password'), { passwordActual, passwordNueva });
  }
}
