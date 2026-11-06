import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

/** Pantalla del menú que aún no tiene diseño en el mockup; el endpoint del backend ya existe. */
@Component({
  selector: 'sc-pendiente',
  imports: [RouterLink],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / {{ titulo }}</p>
        <div class="encabezado"><div><h1>{{ titulo }}</h1></div></div>
      </div>
      <section class="tarjeta vacio">
        <span class="material-symbols-outlined">construction</span>
        <p>Esta pantalla llega en la siguiente etapa del frontend. El backend ya la soporta.</p>
      </section>
    </div>
  `,
})
export class Pendiente {
  protected readonly titulo = (inject(ActivatedRoute).snapshot.data['titulo'] as string | undefined) ?? 'En construcción';
}
