import { Component, OnInit, inject, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { CatalogosService, FilaCatalogo } from '../servicios/catalogos.service';

/**
 * Selector "Toda la empresa / una sede" para los reportes. Solo aparece si la empresa tiene más de una sede activa;
 * con una sola no hay nada que separar. El valor es el id de la sede o {@code null} para toda la empresa.
 */
@Component({
  selector: 'sc-selector-sede',
  imports: [FormsModule],
  template: `
    @if (sedes().length > 1) {
      <div class="campo selector-sede">
        <label for="sel-sede">Sede</label>
        <select id="sel-sede" class="entrada" [ngModel]="sede()" (ngModelChange)="sede.set($event)">
          <option [ngValue]="null">Toda la empresa</option>
          @for (s of sedes(); track s.id) { <option [ngValue]="s.id">{{ s['nombre'] }}</option> }
        </select>
      </div>
    }
  `,
  styles: `.selector-sede { min-width: 200px; margin: 0; }`,
})
export class SelectorSede implements OnInit {
  private readonly catalogos = inject(CatalogosService);

  /** Sede elegida; {@code null} = toda la empresa. */
  readonly sede = model<number | null>(null);
  protected readonly sedes = signal<FilaCatalogo[]>([]);

  ngOnInit(): void {
    this.catalogos.listar('/api/sedes', true).subscribe({ next: l => this.sedes.set(l), error: () => this.sedes.set([]) });
  }
}
