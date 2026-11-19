import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Cliente, ClienteRequest, ListaPrecio, TipoDocumento } from '../../core/api/modelos';
import { ClientesService } from '../../core/servicios/clientes.service';
import { Avisos } from '../../core/ui/avisos';

/** Tipos de documento con su nombre. */
const TIPOS: { codigo: TipoDocumento; nombre: string }[] = [
  { codigo: 'CC', nombre: 'Cédula de ciudadanía' },
  { codigo: 'NIT', nombre: 'NIT (con dígito de verificación)' },
  { codigo: 'CE', nombre: 'Cédula de extranjería' },
  { codigo: 'PAS', nombre: 'Pasaporte' },
];

/**
 * Formulario de un cliente: crea o edita. Se reutiliza en la pantalla de clientes y, en la spec 005, desde el POS
 * (creación rápida). El documento, la autorización de datos y los permisos los valida el backend; aquí solo se evita
 * enviar campos obligatorios vacíos.
 */
@Component({
  selector: 'sc-cliente-formulario',
  imports: [FormsModule],
  template: `
    <form class="formulario cliente-form" (ngSubmit)="guardar()">
      <div class="campo"><label for="cf-tipo">Tipo de documento</label>
        <select id="cf-tipo" name="tipo" class="entrada" [ngModel]="tipo()" (ngModelChange)="tipo.set($event)">
          @for (t of tipos; track t.codigo) { <option [ngValue]="t.codigo">{{ t.nombre }}</option> }
        </select></div>
      <div class="campo"><label for="cf-num">Número</label>
        <input id="cf-num" name="numero" class="entrada" maxlength="25" autocomplete="off" required
          [placeholder]="tipo() === 'NIT' ? '900123456-7' : ''" [ngModel]="numero()" (ngModelChange)="numero.set($event)"></div>
      <div class="campo ancho"><label for="cf-nom">Nombre o razón social</label>
        <input id="cf-nom" name="nombre" class="entrada" maxlength="200" required [ngModel]="nombre()" (ngModelChange)="nombre.set($event)"></div>
      <label class="opcion ancho"><input type="checkbox" name="acepta" [ngModel]="acepta()" (ngModelChange)="acepta.set($event)">
        El cliente autoriza el tratamiento de sus datos personales (Ley 1581 de 2012). Sin esta autorización no se guardan teléfono, correo ni dirección.</label>
      @if (acepta()) {
        <div class="campo"><label for="cf-tel">Teléfono</label>
          <input id="cf-tel" name="telefono" class="entrada" maxlength="30" inputmode="tel" [ngModel]="telefono()" (ngModelChange)="telefono.set($event)"></div>
        <div class="campo"><label for="cf-cor">Correo</label>
          <input id="cf-cor" name="correo" type="email" class="entrada" maxlength="150" [ngModel]="correo()" (ngModelChange)="correo.set($event)"></div>
        <div class="campo ancho"><label for="cf-dir">Dirección</label>
          <input id="cf-dir" name="direccion" class="entrada" maxlength="250" [ngModel]="direccion()" (ngModelChange)="direccion.set($event)"></div>
      }
      @if (puedeCondiciones()) {
        <div class="campo"><label for="cf-lista">Lista de precios</label>
          <select id="cf-lista" name="lista" class="entrada" [ngModel]="listaId()" (ngModelChange)="listaId.set($event)">
            <option [ngValue]="null">General</option>
            @for (l of listas(); track l.id) { @if (!l.esGeneral && l.activo) { <option [ngValue]="l.id">{{ l.nombre }}</option> } }
          </select></div>
        <div class="campo"><label for="cf-cupo">Cupo de crédito</label>
          <input id="cf-cupo" name="cupo" type="number" min="0" class="entrada num" [ngModel]="cupo()" (ngModelChange)="cupo.set($event === '' ? null : +$event)"></div>
        <div class="campo"><label for="cf-plazo">Plazo (días)</label>
          <input id="cf-plazo" name="plazo" type="number" min="0" max="365" class="entrada num" [ngModel]="plazo()" (ngModelChange)="plazo.set($event === '' ? null : +$event)"></div>
      }
      @if (cliente()) {
        <label class="opcion ancho"><input type="checkbox" name="activo" [ngModel]="activo()" (ngModelChange)="activo.set($event)"> Activo (se le puede vender)</label>
      }
      <div class="fila ancho">
        <button type="submit" class="boton primario" [disabled]="guardando() || !completo()">{{ cliente() ? 'Guardar cambios' : 'Crear cliente' }}</button>
        <button type="button" class="boton" (click)="cancelado.emit()">Cancelar</button>
      </div>
    </form>
  `,
  styles: `
    .cliente-form { grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); .ancho { grid-column: 1 / -1; } }
    .opcion { display: flex; align-items: flex-start; gap: 8px; font-size: 13px; input { margin-top: 3px; accent-color: var(--sc-primario); } }
  `,
})
export class ClienteFormulario implements OnInit {
  private readonly clientes = inject(ClientesService);
  private readonly avisos = inject(Avisos);

  /** Cliente que se edita; null para crear uno nuevo. */
  readonly cliente = input<Cliente | null>(null);
  /** Si la sesión puede fijar cupo, plazo y lista (dueño o supervisor de ventas). */
  readonly puedeCondiciones = input(false);
  /** Listas de precios para elegir la del cliente. */
  readonly listas = input<ListaPrecio[]>([]);
  readonly guardado = output<Cliente>();
  readonly cancelado = output<void>();

  protected readonly tipos = TIPOS;
  protected readonly tipo = signal<TipoDocumento>('CC');
  protected readonly numero = signal('');
  protected readonly nombre = signal('');
  protected readonly acepta = signal(false);
  protected readonly telefono = signal('');
  protected readonly correo = signal('');
  protected readonly direccion = signal('');
  protected readonly listaId = signal<number | null>(null);
  protected readonly cupo = signal<number | null>(null);
  protected readonly plazo = signal<number | null>(null);
  protected readonly activo = signal(true);
  protected readonly guardando = signal(false);
  protected readonly completo = computed(() => this.numero().trim().length > 0 && this.nombre().trim().length > 0);

  ngOnInit(): void {
    const c = this.cliente();
    if (c) {
      this.tipo.set(c.tipoDocumento ?? 'CC');
      this.numero.set(c.numeroDocumento ?? '');
      this.nombre.set(c.nombre ?? '');
      this.acepta.set(!!c.aceptaDatosEn);
      this.telefono.set(c.telefono ?? '');
      this.correo.set(c.correo ?? '');
      this.direccion.set(c.direccion ?? '');
      this.listaId.set(c.listaPrecioId ?? null);
      this.cupo.set(c.cupoCredito ?? null);
      this.plazo.set(c.plazoDias ?? null);
      this.activo.set(c.activo ?? true);
    }
  }

  /** Datos para el backend: sin contacto si no hay autorización; sin cupo, plazo ni lista si la sesión no puede fijarlos. */
  protected datos(): ClienteRequest {
    const acepta = this.acepta();
    const condiciones = this.puedeCondiciones();
    const texto = (v: string) => (acepta && v.trim() ? v.trim() : null);
    return {
      tipoDocumento: this.tipo(), numeroDocumento: this.numero().trim(), nombre: this.nombre().trim(),
      telefono: texto(this.telefono()), correo: texto(this.correo()), direccion: texto(this.direccion()),
      aceptaDatos: acepta,
      listaPrecioId: condiciones ? this.listaId() : null,
      cupoCredito: condiciones ? this.cupo() : null,
      plazoDias: condiciones ? this.plazo() : null,
      activo: this.cliente() ? this.activo() : null,
    } as ClienteRequest;
  }

  protected guardar(): void {
    if (!this.completo() || this.guardando()) {
      return;
    }
    this.guardando.set(true);
    const c = this.cliente();
    const peticion = c ? this.clientes.editar(c.id!, this.datos()) : this.clientes.crear(this.datos());
    peticion.subscribe({
      next: r => {
        this.guardando.set(false);
        this.avisos.ok(c ? 'Cliente actualizado.' : `Cliente ${r.nombre} creado.`);
        this.guardado.emit(r);
      },
      error: e => {
        this.guardando.set(false);
        this.avisos.error(e);
      },
    });
  }
}
