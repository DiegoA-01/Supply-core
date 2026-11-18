import { Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { UnidadMedida } from '../../core/api/modelos';
import { CatalogosService, Conversion, FilaCatalogo } from '../../core/servicios/catalogos.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { CampoCatalogo, CATALOGOS, ConfigCatalogo } from './catalogos';

/**
 * Pantalla de un catálogo simple de la empresa (sedes, centros de costo, bodegas, categorías, unidades de medida). Una
 * sola pantalla sirve a los cinco: lo que cambia (campos, roles, ruta de la API) sale de {@link CATALOGOS}. Desactivar no
 * borra nada: lo que ya usó el dato conserva su historia.
 */
@Component({
  selector: 'sc-catalogo',
  imports: [FormsModule, RouterLink],
  templateUrl: './catalogo.html',
  styleUrl: './catalogo.scss',
})
export class Catalogo {
  private readonly servicio = inject(CatalogosService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  /** Clave del catálogo (de la ruta): sedes, centros-costo, bodegas, categorias o unidades-medida. */
  readonly catalogo = input.required<string>();

  protected readonly config = computed<ConfigCatalogo | null>(() => CATALOGOS.find(c => c.clave === this.catalogo()) ?? null);
  protected readonly filas = signal<FilaCatalogo[] | null>(null);
  protected readonly sedes = signal<FilaCatalogo[]>([]);
  protected readonly unidades = signal<UnidadMedida[]>([]);
  protected readonly estado = signal<'activos' | 'inactivos' | 'todos'>('activos');
  protected readonly texto = signal('');
  protected readonly trabajando = signal(false);

  // Formulario
  protected readonly abierto = signal(false);
  protected readonly editando = signal<FilaCatalogo | null>(null);
  protected readonly valores = signal<Record<string, string>>({});

  // Convertidor (solo unidades de medida)
  protected readonly convCantidad = signal<number | null>(null);
  protected readonly convDesde = signal<number | null>(null);
  protected readonly convHacia = signal<number | null>(null);
  protected readonly conversion = signal<Conversion | null>(null);

  protected readonly puedeEditar = computed(() => !!this.config() && this.sesion.tieneRol(this.config()!.roles) && !this.sesion.soloLectura());
  protected readonly visibles = computed(() => {
    const t = this.texto().trim().toLowerCase();
    const c = this.config();
    return (this.filas() ?? []).filter(f => !t || (c?.campos ?? []).some(k => String(f[k.mostrar ?? k.clave] ?? '').toLowerCase().includes(t)));
  });

  constructor() {
    effect(() => {
      const c = this.config();
      untracked(() => {
        this.abierto.set(false);
        this.filas.set(null);
        this.texto.set('');
        this.conversion.set(null);
        if (c) {
          this.cargar();
          if (c.campos.some(k => k.tipo === 'sede')) {
            this.servicio.listar('/api/sedes', true).subscribe({ next: l => this.sedes.set(l), error: () => undefined });
          }
          if (c.clave === 'unidades-medida') {
            this.servicio.listar('/api/unidades-medida', true).subscribe({ next: l => this.unidades.set(l as unknown as UnidadMedida[]), error: () => undefined });
          }
        }
      });
    });
  }

  protected cargar(): void {
    const c = this.config();
    if (!c) {
      return;
    }
    const e = this.estado();
    this.servicio.listar(c.ruta, e === 'todos' ? null : e === 'activos').subscribe({
      next: l => this.filas.set(l),
      error: err => {
        this.filas.set([]);
        this.avisos.error(err);
      },
    });
  }

  /** @param e filtro de estado */
  protected filtrar(e: 'activos' | 'inactivos' | 'todos'): void {
    this.estado.set(e);
    this.cargar();
  }

  /** @param f fila; texto que se muestra en la tabla para un campo */
  protected celda(f: FilaCatalogo, k: CampoCatalogo): string {
    const v = f[k.mostrar ?? k.clave];
    if (v == null || v === '') {
      return '—';
    }
    if (k.tipo === 'seleccion') {
      return k.opciones?.find(o => o.valor === v)?.texto ?? String(v);
    }
    return String(v);
  }

  protected nuevo(): void {
    this.editando.set(null);
    this.valores.set({});
    this.abierto.set(true);
  }

  /** @param f fila que se edita */
  protected editar(f: FilaCatalogo): void {
    const v: Record<string, string> = {};
    for (const k of this.config()?.campos ?? []) {
      v[k.clave] = f[k.clave] == null ? '' : String(f[k.clave]);
    }
    this.editando.set(f);
    this.valores.set(v);
    this.abierto.set(true);
  }

  /** @param k campo; @param valor texto escrito */
  protected escribir(k: CampoCatalogo, valor: string): void {
    this.valores.update(v => ({ ...v, [k.clave]: valor }));
  }

  /** @param k campo; si se puede cambiar en este momento (al editar, los que son fijos quedan bloqueados) */
  protected editable(k: CampoCatalogo): boolean {
    return !this.editando() || k.editable !== false;
  }

  protected guardar(): void {
    const c = this.config();
    if (!c) {
      return;
    }
    const fila = this.editando();
    const datos: Record<string, unknown> = {};
    for (const k of c.campos) {
      if (fila && k.editable === false) {
        continue;                                   // lo fijo no se manda al editar
      }
      const crudo = (this.valores()[k.clave] ?? '').trim();
      if (!crudo) {
        if (k.requerido) {
          this.avisos.ok(`Escribe ${k.etiqueta.toLowerCase()}.`);
          return;
        }
        continue;
      }
      if (k.tipo === 'numero' || k.tipo === 'sede') {
        const n = Number(crudo);
        if (Number.isNaN(n) || (k.requerido && k.tipo === 'numero' && n <= 0)) {
          this.avisos.ok(`${k.etiqueta}: escribe un número${k.requerido ? ' mayor que cero' : ''}.`);
          return;
        }
        datos[k.clave] = n;
      } else {
        datos[k.clave] = crudo;
      }
    }
    this.trabajando.set(true);
    (fila?.id ? this.servicio.actualizar(c.ruta, fila.id, datos) : this.servicio.crear(c.ruta, datos)).subscribe({
      next: r => {
        this.trabajando.set(false);
        this.abierto.set(false);
        this.avisos.ok(`${this.nombreDe(r, c)} ${fila ? 'actualizado' : 'creado'}.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param f fila; la activa o la desactiva (con confirmación al desactivar) */
  protected alternar(f: FilaCatalogo): void {
    const c = this.config();
    if (!c || !f.id) {
      return;
    }
    const desactivar = f.activo !== false;
    if (desactivar && !window.confirm(`¿Desactivar "${this.nombreDe(f, c)}"? Deja de ofrecerse, pero lo que ya lo usó conserva su historia.`)) {
      return;
    }
    this.servicio.cambiarEstado(c.ruta, f.id, !desactivar).subscribe({
      next: () => {
        this.avisos.ok(`${this.nombreDe(f, c)} quedó ${desactivar ? 'inactivo' : 'activo'}.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  protected convertir(): void {
    if (this.convCantidad() == null || this.convCantidad()! < 0 || !this.convDesde() || !this.convHacia()) {
      this.avisos.ok('Escribe la cantidad y elige las dos unidades.');
      return;
    }
    this.servicio.convertir(this.convCantidad()!, this.convDesde()!, this.convHacia()!).subscribe({
      next: r => this.conversion.set(r),
      error: e => {
        this.conversion.set(null);
        this.avisos.error(e);
      },
    });
  }

  private nombreDe(f: FilaCatalogo, c: ConfigCatalogo): string {
    return String(f['nombre'] ?? f['codigo'] ?? c.singular);
  }
}
