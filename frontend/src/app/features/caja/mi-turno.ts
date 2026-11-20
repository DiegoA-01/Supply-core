import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AccionAutorizada, Caja, MedioPago, Turno } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { Avisos } from '../../core/ui/avisos';
import { AutorizacionPin } from './autorizacion-pin';
import { MEDIOS } from './caja';

/** Movimiento de efectivo que registra el cajero. */
type TipoMovimiento = 'RETIRO' | 'GASTO' | 'INGRESO';

/** Texto de cada movimiento. */
const TIPOS: Record<TipoMovimiento, string> = { RETIRO: 'Retiro a la bóveda', GASTO: 'Gasto menor', INGRESO: 'Ingreso de efectivo' };

/**
 * Mi turno (spec 005): el cajero abre su turno con una base, registra retiros, gastos e ingresos con la autorización
 * del supervisor y cierra a ciegas: escribe lo que contó sin ver lo esperado; el esperado y las diferencias aparecen
 * solo después de guardar el cierre.
 */
@Component({
  selector: 'sc-mi-turno',
  imports: [FormsModule, RouterLink, PesosPipe, FechaPipe, AutorizacionPin],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Mi turno</h1><p>Abra su caja, registre el efectivo que sale o entra y ciérrela contando a ciegas.</p></div>
        @if (turno()?.estado === 'ABIERTO') { <a class="boton primario" routerLink="/app/pos"><span class="material-symbols-outlined">point_of_sale</span> Ir a vender</a> }</div>

      @if (!cargado()) {
        <p class="cargando">Buscando su turno…</p>
      } @else if (cerrado(); as c) {
        <section class="tarjeta">
          <h2>Turno cerrado</h2>
          <p class="sin-margen">{{ c.caja }} · cerrado {{ c.cerradoEn | fecha: 'hora' }}</p>
          <div class="tabla-contenedor"><table class="tabla">
            <thead><tr><th>Medio</th><th class="num">Esperado</th><th class="num">Contado</th><th class="num">Diferencia</th></tr></thead>
            <tbody>@for (m of medios; track m) {
              <tr><td>{{ textoMedio(m) }}</td><td class="num">{{ valor(c.esperado, m) | pesos }}</td><td class="num">{{ valor(c.contado, m) | pesos }}</td>
                <td class="num" [class.rojo]="valor(c.diferencias, m) !== 0">{{ valor(c.diferencias, m) | pesos }}</td></tr>
            }</tbody>
          </table></div>
          @if (c.requiereAprobacion) {
            <div class="aviso ambar" role="status"><span class="material-symbols-outlined">gavel</span> La diferencia pasa la tolerancia: un supervisor debe aprobarla.</div>
          } @else { <div class="aviso verde" role="status"><span class="material-symbols-outlined">check_circle</span> El turno cuadró dentro de la tolerancia.</div> }
          <button type="button" class="boton" (click)="nuevo()">Abrir otro turno</button>
        </section>
      } @else if (turno(); as t) {
        <section class="tarjeta resumen">
          <div><span class="sub">Caja</span><strong>{{ t.caja }}</strong></div>
          <div><span class="sub">Abierto</span><strong>{{ t.abiertoEn | fecha: 'hora' }}</strong></div>
          <div><span class="sub">Base</span><strong>{{ t.baseApertura | pesos }}</strong></div>
        </section>
        @if (t.debeRetirar) {
          <div class="aviso ambar" role="alert"><span class="material-symbols-outlined">savings</span> La caja pasó su tope de efectivo: haga un retiro a la bóveda.</div>
        }

        <section class="tarjeta">
          <h2>Retiro, gasto o ingreso de efectivo</h2>
          <div class="formulario">
            <div class="campo"><label for="mt-tipo">Tipo</label>
              <select id="mt-tipo" class="entrada" [ngModel]="tipo()" (ngModelChange)="tipo.set($event)">
                @for (k of tipos; track k) { <option [ngValue]="k">{{ textoTipo(k) }}</option> }
              </select></div>
            <div class="campo"><label for="mt-monto">Monto</label>
              <input id="mt-monto" type="number" min="0" step="any" class="entrada" [ngModel]="monto()" (ngModelChange)="monto.set($event)"></div>
            <div class="campo ancho"><label for="mt-motivo">Motivo</label>
              <input id="mt-motivo" class="entrada" maxlength="300" [ngModel]="motivo()" (ngModelChange)="motivo.set($event)"></div>
            <div class="campo"><label for="mt-soporte">Soporte (recibo)</label>
              <input id="mt-soporte" class="entrada" maxlength="100" placeholder="Opcional" [ngModel]="soporte()" (ngModelChange)="soporte.set($event)"></div>
          </div>
          @if (pidiendo()) {
            <sc-autorizacion-pin [accion]="accionDelTipo()" [turnoId]="t.id!" (autorizado)="registrar($event.token)" (cancelado)="pidiendo.set(false)" />
          } @else {
            <button type="button" class="boton" [disabled]="!movimientoValido()" (click)="pidiendo.set(true)">Registrar (pide PIN)</button>
          }
          @if (movimientos().length) {
            <ul class="lista">@for (m of movimientos(); track m.id) {
              <li><span>{{ m.tipo }} · {{ m.motivo }}</span><span class="num">{{ m.monto | pesos }}</span></li>
            }</ul>
          }
        </section>

        <section class="tarjeta">
          <h2>Cierre ciego</h2>
          <p class="sub">Cuente el efectivo y los comprobantes y escriba lo que hay. El esperado aparece después de guardar.</p>
          <div class="formulario">
            @for (m of medios; track m) {
              <div class="campo"><label [for]="'cc-' + m">{{ textoMedio(m) }}</label>
                <input [id]="'cc-' + m" type="number" min="0" step="any" class="entrada" [ngModel]="contado()[m] ?? null" (ngModelChange)="contar(m, $event)"></div>
            }
            <div class="campo ancho"><label for="cc-obs">Observación</label>
              <input id="cc-obs" class="entrada" maxlength="500" placeholder="Opcional" [ngModel]="observacion()" (ngModelChange)="observacion.set($event)"></div>
          </div>
          <button type="button" class="boton primario" [disabled]="contado()['EFECTIVO'] == null || enviando()" (click)="cerrar()">Cerrar turno</button>
        </section>
      } @else {
        <section class="tarjeta">
          <h2>Abrir turno</h2>
          <div class="formulario">
            <div class="campo"><label for="ab-caja">Caja</label>
              <select id="ab-caja" class="entrada" [ngModel]="cajaId()" (ngModelChange)="cajaId.set($event)">
                <option [ngValue]="null">Elija la caja</option>
                @for (c of cajas(); track c.id) { <option [ngValue]="c.id">{{ c.nombre }} · {{ c.sede }}</option> }
              </select></div>
            <div class="campo"><label for="ab-base">Base en efectivo</label>
              <input id="ab-base" type="number" min="0" step="any" class="entrada" [ngModel]="base()" (ngModelChange)="base.set($event)"></div>
          </div>
          <button type="button" class="boton primario" [disabled]="!cajaId() || base() == null || enviando()" (click)="abrir()">Abrir turno</button>
        </section>
      }
    </div>
  `,
  styles: `
    h2 { margin: 0 0 8px; }
    .resumen { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .formulario { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; margin-bottom: 12px; .ancho { grid-column: 1 / -1; } }
    .lista { list-style: none; padding: 0; margin: 12px 0 0; display: grid; gap: 4px; li { display: flex; justify-content: space-between; } }
    td.rojo { color: #b3261e; font-weight: 600; }
  `,
})
export class MiTurno implements OnInit {
  private readonly caja = inject(CajaService);
  private readonly avisos = inject(Avisos);

  protected readonly medios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly tipos = Object.keys(TIPOS) as TipoMovimiento[];
  protected readonly cargado = signal(false);
  protected readonly turno = signal<Turno | null>(null);
  protected readonly cerrado = signal<Turno | null>(null);
  protected readonly cajas = signal<Caja[]>([]);
  protected readonly cajaId = signal<number | null>(null);
  protected readonly base = signal<number | null>(null);
  protected readonly enviando = signal(false);

  protected readonly tipo = signal<TipoMovimiento>('RETIRO');
  protected readonly monto = signal<number | null>(null);
  protected readonly motivo = signal('');
  protected readonly soporte = signal('');
  protected readonly pidiendo = signal(false);

  protected readonly contado = signal<Partial<Record<MedioPago, number>>>({});
  protected readonly observacion = signal('');

  protected readonly movimientos = computed(() => (this.turno()?.movimientos ?? []).filter(m => m.tipo !== 'BASE'));
  protected readonly movimientoValido = computed(() => (this.monto() ?? 0) > 0 && this.motivo().trim().length > 2);
  protected readonly accionDelTipo = computed<AccionAutorizada>(() => this.tipo());

  ngOnInit(): void {
    this.caja.actual().subscribe({
      next: t => {
        this.turno.set(t);
        this.cargado.set(true);
      },
      error: (e: HttpErrorResponse) => {
        this.cargado.set(true);
        if (e.status !== 404) {
          this.avisos.error(e);
        }
        this.cargarCajas();
      },
    });
  }

  protected abrir(): void {
    this.enviando.set(true);
    this.caja.abrir(this.cajaId()!, this.base()!).subscribe({
      next: t => {
        this.enviando.set(false);
        this.turno.set(t);
        this.avisos.ok('Turno abierto.');
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param token permiso de un solo uso del supervisor */
  protected registrar(token: string): void {
    const t = this.turno()!;
    this.pidiendo.set(false);
    this.caja.movimiento(t.id!, { tipo: this.tipo(), monto: this.monto()!, motivo: this.motivo().trim(), soporte: this.soporte().trim() || null, autorizacion: token })
      .subscribe({
        next: nuevo => {
          this.turno.set(nuevo);
          this.monto.set(null);
          this.motivo.set('');
          this.soporte.set('');
          this.avisos.ok('Movimiento registrado.');
        },
        error: e => this.avisos.error(e),
      });
  }

  /** @param m medio; @param valor contado */
  protected contar(m: MedioPago, valor: number | null): void {
    this.contado.update(c => ({ ...c, [m]: valor ?? undefined }));
  }

  protected cerrar(): void {
    const c = this.contado();
    const otros: Partial<Record<MedioPago, number>> = {};
    this.medios.filter(m => m !== 'EFECTIVO').forEach(m => (otros[m] = c[m] ?? 0));
    this.enviando.set(true);
    this.caja.cerrar(this.turno()!.id!, c['EFECTIVO'] ?? 0, otros, this.observacion().trim() || null).subscribe({
      next: t => {
        this.enviando.set(false);
        this.turno.set(null);
        this.cerrado.set(t);
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected nuevo(): void {
    this.cerrado.set(null);
    this.contado.set({});
    this.observacion.set('');
    this.cargarCajas();
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPago): string {
    return MEDIOS[m];
  }

  /** @param k tipo @returns texto */
  protected textoTipo(k: TipoMovimiento): string {
    return TIPOS[k];
  }

  /** @param mapa valores por medio; @param m medio @returns valor (0 si no hay) */
  protected valor(mapa: Record<string, number> | undefined, m: MedioPago): number {
    return mapa?.[m] ?? 0;
  }

  private cargarCajas(): void {
    this.caja.cajas(true).subscribe({ next: c => this.cajas.set(c), error: e => this.avisos.error(e) });
  }
}
