import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Pesos } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe } from '../../core/formato/formato';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Pesos de evaluación: qué tanto cuenta cada criterio (precio, plazo, garantía…) en el puntaje de una cotización. Deben
 * estar todos y sumar 100. Cada cambio crea una versión nueva y cierra la anterior; los análisis ya hechos guardan los
 * pesos con que se calcularon y no cambian. El puntaje lo calcula el backend.
 */
@Component({
  selector: 'sc-pesos',
  imports: [FormsModule, RouterLink, FechaPipe, NumeroPipe],
  templateUrl: './pesos.html',
  styleUrl: './pesos.scss',
})
export class PesosConfig implements OnInit {
  private readonly servicio = inject(ConfiguracionService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly vigente = signal<Pesos | null>(null);
  protected readonly editando = signal(false);
  protected readonly valores = signal<Record<string, number>>({});
  protected readonly trabajando = signal(false);

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());
  /** Suma de lo escrito: se muestra para que quien edita vea cuánto falta o sobra (el backend exige 100). */
  protected readonly suma = computed(() => Object.values(this.valores()).reduce((a, b) => a + (Number(b) || 0), 0));
  protected readonly suma100 = computed(() => Math.abs(this.suma() - 100) < 0.0001);

  ngOnInit(): void {
    this.cargar();
  }

  protected editar(): void {
    this.valores.set(Object.fromEntries((this.vigente()?.pesos ?? []).map(p => [p.codigo!, p.peso ?? 0])));
    this.editando.set(true);
  }

  /** @param codigo criterio; @param valor peso escrito */
  protected cambiar(codigo: string, valor: number): void {
    this.valores.update(v => ({ ...v, [codigo]: valor }));
  }

  protected guardar(): void {
    if (!this.suma100()) {
      this.avisos.ok('Los pesos deben sumar exactamente 100.');
      return;
    }
    if (Object.values(this.valores()).some(v => Number(v) < 0)) {
      this.avisos.ok('Ningún peso puede ser negativo.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.cambiarPesos(Object.fromEntries(Object.entries(this.valores()).map(([k, v]) => [k, Number(v)]))).subscribe({
      next: p => {
        this.trabajando.set(false);
        this.editando.set(false);
        this.vigente.set(p);
        this.avisos.ok('Pesos guardados. Aplican a los análisis nuevos; los anteriores no cambian.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.servicio.pesos().subscribe({ next: p => this.vigente.set(p), error: e => this.avisos.error(e) });
  }
}
