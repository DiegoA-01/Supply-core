import { computed, signal } from '@angular/core';

import { Producto } from '../api/modelos';
import { InventarioService } from '../servicios/inventario.service';

/**
 * Buscador de producto por código o nombre: la persona escribe y elige de la lista. El id sale del texto elegido.
 * Consulta al backend a partir de 2 letras para no cargar todo el catálogo.
 */
export class BuscadorProducto {
  readonly texto = signal('');
  readonly opciones = signal<Producto[]>([]);
  /** Producto elegido (el que coincide exactamente con el texto), o {@code null}. */
  readonly producto = computed(() => this.opciones().find(p => this.etiqueta(p) === this.texto()) ?? null);
  readonly id = computed(() => this.producto()?.id ?? null);
  private espera: ReturnType<typeof setTimeout> | null = null;

  /** @param servicio inventario, para consultar productos */
  constructor(private readonly servicio: InventarioService) {}

  /** @param p producto; texto que se ve en la lista */
  etiqueta(p: Producto): string {
    return `${p.codigo} · ${p.nombre}`;
  }

  /** @param texto lo escrito; pide opciones al backend con una pequeña espera entre teclas */
  escribir(texto: string): void {
    this.texto.set(texto);
    if (this.espera) {
      clearTimeout(this.espera);
    }
    if (texto.trim().length < 2) {
      return;
    }
    this.espera = setTimeout(() => this.servicio.productos({ texto: texto.trim(), tamano: 15 })
      .subscribe({ next: p => this.opciones.set(p.contenido ?? []), error: () => undefined }), 250);
  }

  /** @param p producto que se deja elegido sin escribir (por ejemplo, al editar un borrador) */
  elegir(p: Producto): void {
    this.opciones.set([p]);
    this.texto.set(this.etiqueta(p));
  }

  limpiar(): void {
    this.texto.set('');
    this.opciones.set([]);
  }
}
