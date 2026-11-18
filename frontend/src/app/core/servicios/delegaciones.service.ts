import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Delegacion, DelegacionNueva, DelegadoCandidato } from '../api/modelos';
import { api } from '../http/api';

/** Delegación de aprobaciones por fechas (por ejemplo, durante unas vacaciones). */
@Injectable({ providedIn: 'root' })
export class DelegacionesService {
  private readonly http = inject(HttpClient);

  /** Delegaciones que di, la más reciente primero. */
  otorgadas(): Observable<Delegacion[]> {
    return this.http.get<Delegacion[]>(api('/api/delegaciones/otorgadas'));
  }

  /** Delegaciones que recibí, la más reciente primero. */
  recibidas(): Observable<Delegacion[]> {
    return this.http.get<Delegacion[]>(api('/api/delegaciones/recibidas'));
  }

  /** Usuarios en quienes puedo delegar (solo id y nombre). */
  candidatos(): Observable<DelegadoCandidato[]> {
    return this.http.get<DelegadoCandidato[]>(api('/api/delegaciones/candidatos'));
  }

  /** @param datos delegado, vigencia y motivo; delega las aprobaciones del usuario de la sesión */
  crear(datos: DelegacionNueva): Observable<Delegacion> {
    return this.http.post<Delegacion>(api('/api/delegaciones'), datos);
  }

  /** @param id delegación; la revoca quien la dio o el jefe de compras */
  revocar(id: number): Observable<Delegacion> {
    return this.http.patch<Delegacion>(api(`/api/delegaciones/${id}/revocar`), {});
  }
}
