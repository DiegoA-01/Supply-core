import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AuditoriaPlataforma, EmpresaPlataforma } from '../../core/api/modelos';
import { FechaPipe } from '../../core/formato/formato';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

/** Texto de cada acción de la auditoría de la plataforma. */
const ACCIONES: Record<string, string> = {
  LOGIN: 'Ingreso', MFA: 'Código de verificación', MFA_CONFIGURAR: 'Configuró la app autenticadora',
  EMPRESA_CREAR: 'Creó empresa', EMPRESA_CAMBIAR_PLAN: 'Cambió el plan', EMPRESA_MODULO: 'Cambió un módulo',
  EMPRESA_ESTADO: 'Cambió el estado', PLAN_CREAR: 'Creó plan', PLAN_EDITAR: 'Editó plan',
};

/**
 * Consola del dueño · Auditoría global: lo que se hizo en la plataforma (accesos, altas, planes, módulos y estados).
 * Es de solo lectura y separada de la auditoría de cada empresa.
 */
@Component({
  selector: 'sc-auditoria-global',
  imports: [FormsModule, FechaPipe],
  template: `
    <div class="pagina">
      <div class="encabezado">
        <div><h1>Auditoría global</h1><p>Cada acción en la consola queda aquí, incluidos los intentos fallidos. No se puede editar ni borrar.</p></div>
        <div class="acciones">
          <select class="entrada empresa" aria-label="Empresa" [ngModel]="empresaId()" (ngModelChange)="filtrar($event)">
            <option [ngValue]="null">Todas las empresas</option>
            @for (e of empresas(); track e.id) { <option [ngValue]="e.id">{{ e.razonSocial }}</option> }
          </select>
        </div>
      </div>
      <section class="tarjeta sin-relleno">
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Fecha</th><th>Acción</th><th>Resultado</th><th class="oculto-movil">Empresa</th><th class="oculto-movil">Detalle</th><th class="oculto-movil">IP</th></tr></thead>
            <tbody>
              @for (a of registros() ?? []; track a.id) {
                <tr>
                  <td>{{ a.creadoEn | fecha: 'hora' }}</td>
                  <td>{{ accion(a.accion) }}</td>
                  <td><span class="chip" [class.verde]="a.resultado === 'EXITO'" [class.rojo]="a.resultado !== 'EXITO'">{{ a.resultado === 'EXITO' ? 'Éxito' : 'Fallo' }}</span></td>
                  <td class="oculto-movil">{{ empresa(a.empresaId) }}</td>
                  <td class="oculto-movil detalle">{{ a.detalle }}</td>
                  <td class="oculto-movil texto-2">{{ a.ip }}</td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ registros() ? 'Sin registros.' : 'Cargando…' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
        @if (totalPaginas() > 1) {
          <div class="paginas">
            <button type="button" class="boton chico" [disabled]="pagina() === 0" (click)="ir(pagina() - 1)">Anterior</button>
            <span class="texto-chico texto-2">Página {{ pagina() + 1 }} de {{ totalPaginas() }}</span>
            <button type="button" class="boton chico" [disabled]="pagina() + 1 >= totalPaginas()" (click)="ir(pagina() + 1)">Siguiente</button>
          </div>
        }
      </section>
    </div>
  `,
  styles: `
    .empresa { width: 260px; }
    .detalle { max-width: 420px; font-size: 13px; color: var(--sc-texto-2); word-break: break-word; }
    .paginas { display: flex; align-items: center; justify-content: center; gap: 12px; padding: 14px; border-top: 1px solid var(--sc-borde); }
    @media (max-width: 720px) { .empresa { width: 100%; } }
  `,
})
export class AuditoriaGlobal implements OnInit {
  private readonly servicio = inject(PlataformaService);
  private readonly avisos = inject(Avisos);

  protected readonly registros = signal<AuditoriaPlataforma[] | null>(null);
  protected readonly empresas = signal<EmpresaPlataforma[]>([]);
  protected readonly empresaId = signal<number | null>(null);
  protected readonly pagina = signal(0);
  protected readonly totalPaginas = signal(0);
  private readonly nombres = computed(() => new Map(this.empresas().map(e => [e.id, e.razonSocial])));

  ngOnInit(): void {
    this.servicio.empresas().subscribe({ next: l => this.empresas.set(l), error: () => undefined });
    this.cargar();
  }

  /** @param codigo acción registrada */
  protected accion(codigo: string | undefined): string {
    return (codigo && ACCIONES[codigo]) || codigo || '—';
  }

  /** @param id empresa afectada ({@code null} en accesos a la consola) */
  protected empresa(id: number | undefined): string {
    return id ? (this.nombres().get(id) ?? `#${id}`) : '—';
  }

  /** @param id empresa ({@code null} = todas) */
  protected filtrar(id: number | null): void {
    this.empresaId.set(id);
    this.ir(0);
  }

  /** @param pagina página a mostrar */
  protected ir(pagina: number): void {
    this.pagina.set(pagina);
    this.cargar();
  }

  private cargar(): void {
    this.registros.set(null);
    this.servicio.auditoria(this.empresaId(), this.pagina()).subscribe({
      next: p => {
        this.registros.set(p.contenido ?? []);
        this.totalPaginas.set(p.totalPaginas ?? 0);
      },
      error: e => {
        this.registros.set([]);
        this.avisos.error(e);
      },
    });
  }
}
