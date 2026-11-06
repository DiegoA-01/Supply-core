import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Proveedor } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, PuntajePipe } from '../../core/formato/formato';
import { ReportesService } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/** Proveedores de la empresa: los que se registraron en el portal esperan que compras los habilite. */
@Component({
  selector: 'sc-proveedores',
  imports: [RouterLink, Estado, FechaPipe, PuntajePipe],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / Proveedores</p>
        <div class="encabezado">
          <div><h1>Proveedores</h1><p>Se registran solos en el portal; solo cotizan cuando compras los habilita.</p></div>
          <div class="acciones pastillas">
            @for (e of estados; track e.texto) {
              <button type="button" class="pastilla" [class.activa]="estado() === e.valor" (click)="filtrar(e.valor)">{{ e.texto }}</button>
            }
          </div>
        </div>
      </div>
      <section class="tarjeta sin-relleno">
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Proveedor</th><th class="oculto-movil">Contacto</th><th>Estado</th><th class="num">Score</th><th class="oculto-movil">Registro</th><th></th></tr></thead>
            <tbody>
              @for (p of lista() ?? []; track p.id) {
                <tr>
                  <td><strong>{{ p.razonSocial }}</strong><span class="sub">NIT {{ p.nit }}</span></td>
                  <td class="oculto-movil">{{ p.correoContacto }}<span class="sub">{{ p.telefono }}</span></td>
                  <td><sc-estado [valor]="p.estado" /></td>
                  <td class="num">{{ p.scoreActual | puntaje }}</td>
                  <td class="oculto-movil">{{ p.creadoEn | fecha }}</td>
                  <td class="num">
                    @if (p.estado === 'PENDIENTE' && puedeDecidir()) {
                      <div class="fila fin">
                        <button type="button" class="boton chico primario" (click)="habilitar(p)">Habilitar</button>
                        <button type="button" class="boton chico peligro" (click)="rechazar(p)">Rechazar</button>
                      </div>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="6" class="vacio">{{ lista() ? 'No hay proveedores con este filtro.' : 'Cargando…' }}</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: `.fin { justify-content: flex-end; }`,
})
export class Proveedores implements OnInit {
  private readonly servicio = inject(ReportesService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  protected readonly estados = [
    { valor: null, texto: 'Todos' },
    { valor: 'PENDIENTE', texto: 'Por habilitar' },
    { valor: 'HABILITADO', texto: 'Habilitados' },
  ];
  protected readonly estado = signal<string | null>(null);
  protected readonly lista = signal<Proveedor[] | null>(null);
  protected readonly puedeDecidir = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  ngOnInit(): void {
    this.cargar();
  }

  /** @param estado estado del proveedor ({@code null} = todos) */
  protected filtrar(estado: string | null): void {
    this.estado.set(estado);
    this.cargar();
  }

  /** @param p proveedor pendiente */
  protected habilitar(p: Proveedor): void {
    this.servicio.habilitar(p.id!).subscribe({
      next: () => {
        this.avisos.ok(`${p.razonSocial} quedó habilitado y ya puede cotizar.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p proveedor pendiente */
  protected rechazar(p: Proveedor): void {
    const motivo = window.prompt(`Motivo para rechazar a ${p.razonSocial}:`);
    if (!motivo?.trim()) {
      return;
    }
    this.servicio.rechazar(p.id!, motivo.trim()).subscribe({
      next: () => {
        this.avisos.ok(`${p.razonSocial} quedó rechazado.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargar(): void {
    this.lista.set(null);
    this.servicio.proveedores(this.estado()).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }
}
