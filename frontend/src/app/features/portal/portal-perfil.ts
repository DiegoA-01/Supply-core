import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Categoria, Evaluacion, Pagina, Proveedor } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PuntajePipe } from '../../core/formato/formato';
import { PortalService } from '../../core/servicios/portal.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Portal · perfil del proveedor: sus datos, el estado de su habilitación, las categorías en que cotiza y las calificaciones
 * que ha recibido de la empresa. El proveedor corrige su contacto y sus categorías; la razón social y el NIT los cambia la
 * empresa cliente. El score lo calcula el sistema al cerrar cada recepción.
 */
@Component({
  selector: 'sc-portal-perfil',
  imports: [FormsModule, Estado, FechaPipe, NumeroPipe, PuntajePipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Mi perfil</h1><p>Tus datos de contacto, las categorías en que cotizas y cómo te califican tus clientes.</p></div></div>

      @if (proveedor(); as p) {
        <div class="rejilla dos">
          <section class="tarjeta">
            <div class="fila titulo"><h2>{{ p.razonSocial }}</h2><sc-estado [valor]="p.estado" /></div>
            @if (editando()) {
              <form class="formulario" (ngSubmit)="guardar()">
                <div class="campo"><label for="pc">Correo de contacto</label><input id="pc" name="pc" type="email" class="entrada" [ngModel]="correo()" (ngModelChange)="correo.set($event)"></div>
                <div class="campo"><label for="pt">Teléfono</label><input id="pt" name="pt" class="entrada" [ngModel]="telefono()" (ngModelChange)="telefono.set($event)"></div>
                <div class="campo"><label for="pd">Dirección</label><input id="pd" name="pd" class="entrada" [ngModel]="direccion()" (ngModelChange)="direccion.set($event)"></div>
                <fieldset class="categorias">
                  <legend>Categorías en que cotizas</legend>
                  @for (c of categorias(); track c.id) {
                    <label class="check"><input type="checkbox" [checked]="categoriaIds().includes(c.id!)" (change)="alternar(c.id!)"> {{ c.nombre }}</label>
                  }
                </fieldset>
                <div class="fila">
                  <button type="submit" class="boton primario" [disabled]="trabajando()">Guardar</button>
                  <button type="button" class="boton" (click)="editando.set(false)">Cancelar</button>
                </div>
              </form>
            } @else {
              <div class="pares">
                <div class="par"><span>NIT</span><span>{{ p.nit }}</span></div>
                <div class="par"><span>Correo</span><span>{{ p.correoContacto ?? '—' }}</span></div>
                <div class="par"><span>Teléfono</span><span>{{ p.telefono ?? '—' }}</span></div>
                <div class="par"><span>Dirección</span><span>{{ p.direccion ?? '—' }}</span></div>
                <div class="par"><span>Tu calificación</span><span><strong>{{ p.scoreActual | puntaje }}</strong></span></div>
                @if (p.motivoEstado) { <div class="par"><span>Motivo del estado</span><span>{{ p.motivoEstado }}</span></div> }
              </div>
              <h3>Categorías en que cotizas</h3>
              <div class="chips">@for (c of p.categorias ?? []; track c.id) { <span class="chip naranja">{{ c.nombre }}</span> } @empty { <span class="texto-2">Ninguna</span> }</div>
              <p class="texto-chico texto-2">La razón social y el NIT los cambia la empresa a la que le cotizas.</p>
              <button type="button" class="boton margen" (click)="editar()">Editar mis datos</button>
            }
          </section>

          <section class="tarjeta">
            <h2>Cómo te califican</h2>
            <p class="texto-chico texto-2">Cada vez que el cliente cierra la recepción de una orden, el sistema te califica en puntualidad, cantidad, calidad y precio.</p>
            @if (evaluaciones(); as ev) {
              <div class="tabla-contenedor">
                <table class="tabla">
                  <thead><tr><th>Fecha</th><th class="num">Puntualidad</th><th class="num">Cantidad</th><th class="num">Calidad</th><th class="num">Precio</th><th class="num">Total</th></tr></thead>
                  <tbody>
                    @for (e of ev.contenido ?? []; track e.id) {
                      <tr>
                        <td class="nowrap">{{ e.creadoEn | fecha }}</td>
                        <td class="num">{{ e.puntualidad | numero }}</td><td class="num">{{ e.cumplimientoCantidad | numero }}</td>
                        <td class="num">{{ e.calidad | numero }}</td><td class="num">{{ e.precio | numero }}</td>
                        <td class="num"><strong>{{ e.score | puntaje }}</strong></td>
                      </tr>
                    } @empty { <tr><td colspan="6" class="vacio">Todavía no tienes calificaciones.</td></tr> }
                  </tbody>
                </table>
              </div>
              @if ((ev.totalPaginas ?? 0) > 1) {
                <div class="fila paginacion">
                  <button type="button" class="boton chico" [disabled]="pagina() === 0" (click)="cargarEvaluaciones(pagina() - 1)">Anterior</button>
                  <span class="texto-chico texto-2">Página {{ pagina() + 1 }} de {{ ev.totalPaginas }}</span>
                  <button type="button" class="boton chico" [disabled]="pagina() + 1 >= (ev.totalPaginas ?? 0)" (click)="cargarEvaluaciones(pagina() + 1)">Siguiente</button>
                </div>
              }
            } @else { <p class="texto-2">Cargando…</p> }
          </section>
        </div>
      } @else {
        <section class="tarjeta"><p class="texto-2">Cargando…</p></section>
      }
    </div>
  `,
  styles: `
    .rejilla.dos { grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); align-items: start; }
    .titulo { justify-content: space-between; h2 { margin: 0; } }
    .chips { display: flex; gap: 8px; flex-wrap: wrap; }
    .margen { margin-top: 12px; }
    .nowrap { white-space: nowrap; }
    .paginacion { justify-content: center; margin-top: 10px; }
    .categorias { border: 1px solid #e6e6e6; border-radius: 10px; padding: 10px 14px; display: grid; gap: 6px; legend { font-weight: 600; padding: 0 6px; } }
    .check { display: flex; align-items: center; gap: 8px; font-size: 14px; cursor: pointer; }
  `,
})
export class PortalPerfil implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly avisos = inject(Avisos);

  protected readonly proveedor = signal<Proveedor | null>(null);
  protected readonly categorias = signal<Categoria[]>([]);
  protected readonly evaluaciones = signal<Pagina<Evaluacion> | null>(null);
  protected readonly pagina = signal(0);
  protected readonly editando = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly correo = signal('');
  protected readonly telefono = signal('');
  protected readonly direccion = signal('');
  protected readonly categoriaIds = signal<number[]>([]);

  ngOnInit(): void {
    this.portal.proveedor().subscribe({ next: p => this.proveedor.set(p), error: e => this.avisos.error(e) });
    this.portal.categorias().subscribe({ next: l => this.categorias.set(l), error: () => undefined });
    this.cargarEvaluaciones(0);
  }

  /** @param p número de página desde 0 */
  protected cargarEvaluaciones(p: number): void {
    this.pagina.set(p);
    this.portal.evaluaciones(p).subscribe({ next: e => this.evaluaciones.set(e), error: () => this.evaluaciones.set({ contenido: [], pagina: 0, tamano: 10, totalElementos: 0, totalPaginas: 0 }) });
  }

  protected editar(): void {
    const p = this.proveedor();
    if (!p) {
      return;
    }
    this.correo.set(p.correoContacto ?? '');
    this.telefono.set(p.telefono ?? '');
    this.direccion.set(p.direccion ?? '');
    this.categoriaIds.set((p.categorias ?? []).map(c => c.id!));
    this.editando.set(true);
  }

  /** @param id categoría; la marca o la desmarca */
  protected alternar(id: number): void {
    this.categoriaIds.update(l => (l.includes(id) ? l.filter(x => x !== id) : [...l, id]));
  }

  protected guardar(): void {
    if (!this.categoriaIds().length) {
      this.avisos.ok('Elige al menos una categoría: de ahí salen tus invitaciones a cotizar.');
      return;
    }
    this.trabajando.set(true);
    this.portal.actualizarProveedor({
      correoContacto: this.correo().trim() || undefined, telefono: this.telefono().trim() || undefined,
      direccion: this.direccion().trim() || undefined, categoriaIds: this.categoriaIds(),
    }).subscribe({
      next: p => {
        this.trabajando.set(false);
        this.editando.set(false);
        this.proveedor.set(p);
        this.avisos.ok('Datos actualizados.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
