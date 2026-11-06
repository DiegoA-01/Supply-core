import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/** Raíz: cada zona (empresa, portal del proveedor, consola del dueño) trae su propio marco. */
@Component({
  selector: 'sc-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
