import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { mensajeError } from '../http/api';

/** Mensajes cortos al pie de la pantalla. */
@Injectable({ providedIn: 'root' })
export class Avisos {
  private readonly snack = inject(MatSnackBar);

  /** @param mensaje confirmación de una acción */
  ok(mensaje: string): void {
    this.snack.open(mensaje, 'Cerrar', { duration: 4000 });
  }

  /** @param error error del backend o de la red */
  error(error: unknown): void {
    this.snack.open(mensajeError(error), 'Cerrar', { duration: 7000 });
  }
}

/**
 * Descarga un archivo recibido del backend con el nombre indicado.
 *
 * @param archivo contenido
 * @param nombre  nombre sugerido
 */
export function descargar(archivo: Blob, nombre: string): void {
  const url = URL.createObjectURL(archivo);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
