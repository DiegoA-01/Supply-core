import { Component, OnInit, inject, signal } from '@angular/core';

import { CargaCatalogo, PortalCatalogoItem } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CatalogosProveedorService } from '../../core/servicios/catalogos.proveedor.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Portal · Mi catálogo: el proveedor sube lo que vende y a qué precio (Excel). La empresa lo usa como referencia para
 * comparar y decidir a quién cotizar; el proveedor no ve nada del inventario de su cliente.
 */
@Component({
  selector: 'sc-portal-catalogo',
  imports: [PesosPipe, FechaPipe],
  template: `
    <div class="pagina">
      <div class="encabezado"><div><h1>Mi catálogo</h1>
        <p>Sube lo que vendes y a qué precio. Tu cliente lo usa como referencia para saber qué ofreces; cada vez que subas el archivo se reemplaza el anterior.</p></div></div>

      <section class="tarjeta">
        <h2>Subir catálogo (Excel .xlsx)</h2>
        <p class="texto-chico texto-2">Primera fila con estos encabezados: <code>codigo</code>, <code>descripcion</code>, <code>marca</code>, <code>unidad</code>, <code>precio</code>, <code>incluye_iva</code> (si/no) y <code>vigente_hasta</code> (aaaa-mm-dd, opcional). Hasta 5.000 artículos.</p>
        <div class="fila subir">
          <input id="arch" type="file" accept=".xlsx" class="entrada" (change)="elegir($event)">
          <button type="button" class="boton primario" [disabled]="!archivo() || subiendo()" (click)="subir()">
            <span class="material-symbols-outlined">upload</span> {{ subiendo() ? 'Subiendo…' : 'Subir' }}</button>
        </div>
        @if (resultado(); as r) {
          @if (r.aplicado) {
            <div class="aviso verde" role="status"><span class="material-symbols-outlined">check_circle</span>
              Catálogo actualizado: {{ r.nuevos }} nuevos, {{ r.actualizados }} con cambios, {{ r.retirados }} retirados.</div>
          } @else {
            <div class="aviso rojo" role="alert"><span class="material-symbols-outlined">error</span>
              <div>No se guardó nada. Corrige estas filas y vuelve a subir el archivo:
                <ul>@for (e of r.errores ?? []; track $index) { <li>Fila {{ e.fila }}: {{ e.mensaje }}</li> }</ul></div></div>
          }
        }
      </section>

      <section class="tarjeta sin-relleno">
        <div class="tabla-contenedor">
          <table class="tabla">
            <thead><tr><th>Código</th><th>Descripción</th><th>Marca</th><th>Unidad</th><th class="num">Precio</th><th>Vigente hasta</th><th>Estado</th></tr></thead>
            <tbody>
              @for (c of items(); track c.codigoProveedor) {
                <tr [class.inactivo]="!c.activo">
                  <td>{{ c.codigoProveedor }}</td><td>{{ c.descripcion }}</td><td>{{ c.marca ?? '—' }}</td><td>{{ c.unidad ?? '—' }}</td>
                  <td class="num">{{ c.precio | pesos }}<span class="sub">{{ c.incluyeIva ? 'con IVA' : 'sin IVA' }}</span></td>
                  <td>{{ c.vigenteHasta ? (c.vigenteHasta | fecha) : '—' }}</td>
                  <td><span class="chip" [class.verde]="c.activo">{{ c.activo ? 'Publicado' : 'Retirado' }}</span></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="vacio">Aún no has subido tu catálogo.</td></tr>
              }
            </tbody>
          </table>
        </div>
      </section>
    </div>
  `,
  styles: `
    h2 { margin: 0 0 6px; }
    .subir { align-items: center; gap: 12px; flex-wrap: wrap; margin: 12px 0; input { max-width: 360px; } }
    .sub { display: block; font-size: 12px; color: var(--sc-texto-2); }
    .inactivo td { color: var(--sc-texto-3); }
    ul { margin: 6px 0 0; padding-left: 18px; }
  `,
})
export class PortalCatalogo implements OnInit {
  private readonly servicio = inject(CatalogosProveedorService);
  private readonly avisos = inject(Avisos);

  protected readonly items = signal<PortalCatalogoItem[]>([]);
  protected readonly archivo = signal<File | null>(null);
  protected readonly subiendo = signal(false);
  protected readonly resultado = signal<CargaCatalogo | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  /** @param e cambio del campo de archivo */
  protected elegir(e: Event): void {
    this.archivo.set((e.target as HTMLInputElement).files?.[0] ?? null);
    this.resultado.set(null);
  }

  protected subir(): void {
    const a = this.archivo();
    if (!a) {
      return;
    }
    this.subiendo.set(true);
    this.servicio.subir(a).subscribe({
      next: r => {
        this.subiendo.set(false);
        this.resultado.set(r);
        if (r.aplicado) {
          this.cargar();
        }
      },
      error: e => {
        this.subiendo.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.servicio.miCatalogo().subscribe({ next: l => this.items.set(l), error: e => this.avisos.error(e) });
  }
}
