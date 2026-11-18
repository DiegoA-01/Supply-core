import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Pagina, RegistroAuditoria, Usuario } from '../../core/api/modelos';
import { FechaPipe } from '../../core/formato/formato';
import { AuditoriaService, FiltroAuditoria } from '../../core/servicios/auditoria.service';
import { iso } from '../../core/servicios/reportes.service';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { Avisos } from '../../core/ui/avisos';
import { ACCIONES, ENTIDADES, LISTA_ACCIONES, LISTA_ENTIDADES, RUTA_ENTIDAD } from './textos';

/**
 * Auditoría de la empresa: quién hizo qué, cuándo y desde qué dirección. Es de solo lectura y no se puede editar ni
 * borrar; incluye los intentos fallidos. La ven el auditor y el administrador de la empresa. Cada registro guarda el
 * contexto de la acción (nunca contraseñas ni códigos).
 */
@Component({
  selector: 'sc-auditoria',
  imports: [FormsModule, RouterLink, FechaPipe],
  templateUrl: './auditoria.html',
  styleUrl: './auditoria.scss',
})
export class Auditoria implements OnInit {
  private readonly servicio = inject(AuditoriaService);
  private readonly usuarios = inject(UsuariosService);
  private readonly avisos = inject(Avisos);

  protected readonly listaEntidades = LISTA_ENTIDADES;
  protected readonly listaAcciones = LISTA_ACCIONES;
  protected readonly entidades = ENTIDADES;

  protected readonly pagina = signal<Pagina<RegistroAuditoria> | null>(null);
  protected readonly numeroPagina = signal(0);
  protected readonly lista = signal<Usuario[]>([]);
  protected readonly abierto = signal<number | null>(null);

  // Filtros
  protected readonly usuarioId = signal<number | null>(null);
  protected readonly entidad = signal<string | null>(null);
  protected readonly entidadId = signal<number | null>(null);
  protected readonly accion = signal<string | null>(null);
  protected readonly resultado = signal<string | null>(null);
  protected readonly desde = signal('');
  protected readonly hasta = signal('');

  protected readonly hayFiltros = computed(() => !!(this.usuarioId() || this.entidad() || this.entidadId() || this.accion() || this.resultado() || this.desde() || this.hasta()));

  ngOnInit(): void {
    this.usuarios.buscar({ activo: null }).subscribe({ next: p => this.lista.set(p.contenido ?? []), error: () => undefined });
    this.buscar(0);
  }

  /** @param p número de página desde 0 */
  protected buscar(p: number): void {
    if (this.desde() && this.hasta() && this.desde() > this.hasta()) {
      this.avisos.ok('La fecha "desde" no puede ser posterior a "hasta".');
      return;
    }
    this.pagina.set(null);
    this.abierto.set(null);
    this.numeroPagina.set(p);
    this.servicio.buscar(this.filtros(), p).subscribe({
      next: r => this.pagina.set(r),
      error: e => {
        this.pagina.set({ contenido: [], pagina: 0, tamano: 25, totalElementos: 0, totalPaginas: 0 });
        this.avisos.error(e);
      },
    });
  }

  protected limpiar(): void {
    this.usuarioId.set(null);
    this.entidad.set(null);
    this.entidadId.set(null);
    this.accion.set(null);
    this.resultado.set(null);
    this.desde.set('');
    this.hasta.set('');
    this.buscar(0);
  }

  /** @param dias cuántos días hacia atrás (0 = solo hoy); deja el rango listo y busca */
  protected rapido(dias: number): void {
    const hoy = new Date();
    const inicio = new Date();
    inicio.setDate(inicio.getDate() - dias);
    this.desde.set(iso(inicio));
    this.hasta.set(iso(hoy));
    this.buscar(0);
  }

  /** @param codigo código de la acción; texto en español para el selector */
  protected textoDe(codigo: string): string {
    return ACCIONES[codigo] ?? codigo;
  }

  /** @param a registro; texto de la acción en español (o el código si es uno nuevo) */
  protected textoAccion(a: RegistroAuditoria): string {
    return ACCIONES[a.accion ?? ''] ?? a.accion ?? '';
  }

  /** @param a registro; nombre de la entidad afectada */
  protected textoEntidad(a: RegistroAuditoria): string {
    return ENTIDADES[a.entidad ?? ''] ?? a.entidad ?? '';
  }

  /** @param a registro; dirección de la pantalla de la entidad si existe */
  protected enlace(a: RegistroAuditoria): string | null {
    const f = RUTA_ENTIDAD[a.entidad ?? ''];
    return f && a.entidadId ? f(a.entidadId) : null;
  }

  /** @param id registro; abre o cierra su detalle */
  protected alternar(id: number | undefined): void {
    this.abierto.set(this.abierto() === id ? null : (id ?? null));
  }

  private filtros(): FiltroAuditoria {
    return {
      usuarioId: this.usuarioId(), entidad: this.entidad(), entidadId: this.entidadId(), accion: this.accion(), resultado: this.resultado(),
      desde: this.desde() ? `${this.desde()}T00:00:00` : null, hasta: this.hasta() ? `${this.hasta()}T23:59:59` : null,
    };
  }
}
