import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { EmpresaCambios } from '../../core/api/modelos';
import { ConfiguracionService } from '../../core/servicios/configuracion.service';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

/**
 * Datos generales de la empresa y sus reglas de negocio: tope de anticipos, tolerancia del cruce de tres vías y
 * plazo (SLA) para aprobar. Solo el administrador de la empresa los cambia; queda en la auditoría.
 */
@Component({
  selector: 'sc-config-empresa',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div>
        <p class="migas"><a routerLink="/app/inicio">Inicio</a> / <a routerLink="/app/configuracion">Configuración</a> / Datos de la empresa</p>
        <div class="encabezado"><div><h1>Datos y reglas de la empresa</h1>
          <p>Lo que identifica a la empresa y las reglas que el sistema aplica en cada compra. Cada cambio queda en la auditoría.</p></div></div>
      </div>
      <section class="tarjeta">
      <h3>Datos de la empresa</h3>
      <div class="formulario dos">
        <div class="campo"><label for="rs">Razón social</label><input id="rs" class="entrada" [ngModel]="datos().razonSocial" (ngModelChange)="cambiar('razonSocial', $event)"></div>
        <div class="campo"><label for="nit">NIT</label><input id="nit" class="entrada" [ngModel]="datos().nit" (ngModelChange)="cambiar('nit', $event)"></div>
        <div class="campo"><label for="dir">Dirección principal</label><input id="dir" class="entrada" [ngModel]="datos().direccionPrincipal" (ngModelChange)="cambiar('direccionPrincipal', $event)"></div>
        <div class="campo"><label for="mon">Moneda</label><input id="mon" class="entrada" disabled [ngModel]="datos().monedaBase">
          <span class="ayuda">Por ahora todo se maneja en pesos colombianos (COP).</span></div>
      </div>
      <h3 class="separa">Reglas de compra</h3>
      <div class="formulario dos">
        <div class="campo"><label for="tope">Tope de anticipos (% del total de la orden)</label>
          <input id="tope" type="number" min="0" max="100" class="entrada num" [ngModel]="datos().topeAnticipoPct" (ngModelChange)="cambiar('topeAnticipoPct', $event)"></div>
        <div class="campo"><label for="sla">Plazo para aprobar (horas)</label>
          <input id="sla" type="number" min="1" class="entrada num" [ngModel]="datos().slaAprobacionHoras" (ngModelChange)="cambiar('slaAprobacionHoras', $event)">
          <span class="ayuda">Se avisa al 80 % y al vencer se escala a la política de mayor monto.</span></div>
        <div class="campo"><label for="tpct">Tolerancia de factura (%)</label>
          <input id="tpct" type="number" min="0" step="0.01" class="entrada num" [ngModel]="datos().toleranciaFacturaPct" (ngModelChange)="cambiar('toleranciaFacturaPct', $event)"></div>
        <div class="campo"><label for="tmon">Tolerancia de factura (monto)</label>
          <input id="tmon" type="number" min="0" class="entrada num" [ngModel]="datos().toleranciaFacturaMonto" (ngModelChange)="cambiar('toleranciaFacturaMonto', $event)">
          <span class="ayuda">Una factura cuadra si la diferencia con lo recibido cabe en cualquiera de las dos tolerancias.</span></div>
      </div>
      <h3 class="separa">Reabastecimiento</h3>
      <p class="texto-chico texto-2">Con estos valores el plan de reabastecimiento calcula hasta cuándo pedir cada producto. Cada producto puede tener su propio plazo.</p>
      <div class="formulario dos">
        <div class="campo"><label for="plazo">Un pedido tarda en llegar (días hábiles)</label>
          <input id="plazo" type="number" min="0" max="120" class="entrada num" [ngModel]="datos().plazoEntregaDiasDefecto" (ngModelChange)="cambiar('plazoEntregaDiasDefecto', $event)">
          <span class="ayuda">Sin sábados, domingos ni festivos. Ej.: si el proveedor tarda de 6 a 7 días hábiles, pon 7.</span></div>
        <div class="campo"><label for="seg">Días de seguridad (hábiles)</label>
          <input id="seg" type="number" min="0" max="60" class="entrada num" [ngModel]="datos().diasSeguridadDefecto" (ngModelChange)="cambiar('diasSeguridadDefecto', $event)">
          <span class="ayuda">Colchón por si el pedido se demora o se vende más de lo normal.</span></div>
        <div class="campo"><label for="cob">Lo que pido debe alcanzar (días)</label>
          <input id="cob" type="number" min="1" max="365" class="entrada num" [ngModel]="datos().diasCoberturaDefecto" (ngModelChange)="cambiar('diasCoberturaDefecto', $event)">
          <span class="ayuda">Días de calendario que cubre cada pedido después de llegar.</span></div>
      </div>
      <h3 class="separa">Precios de venta</h3>
      <label class="opcion-delegar"><input type="checkbox" [ngModel]="datos().preciosDelegadosCompras" (ngModelChange)="cambiar('preciosDelegadosCompras', $event)">
        El jefe de compras también puede fijar precios de venta</label>
      <p class="texto-chico texto-2">Si no lo marcas, solo el administrador de la empresa cambia precios. Cada cambio queda en el historial.</p>
      <button type="button" class="boton primario separa" [disabled]="trabajando() || sesion.soloLectura()" (click)="guardar()">Guardar cambios</button>
      </section>
    </div>
  `,
  styles: `h3 { margin: 0 0 12px; } .separa { margin-top: 18px; }
    .opcion-delegar { display: flex; align-items: center; gap: 8px; input { accent-color: var(--sc-primario); } }`,
})
export class ConfigEmpresa implements OnInit {
  private readonly servicio = inject(ConfiguracionService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly datos = signal<EmpresaCambios>({ razonSocial: '', nit: '', monedaBase: 'COP' });
  protected readonly trabajando = signal(false);

  ngOnInit(): void {
    const e = this.sesion.empresa();
    if (e) {
      this.datos.set({
        razonSocial: e.razonSocial ?? '', nit: e.nit ?? '', direccionPrincipal: e.direccionPrincipal, monedaBase: e.monedaBase ?? 'COP',
        topeAnticipoPct: e.topeAnticipoPct, toleranciaFacturaPct: e.toleranciaFacturaPct, toleranciaFacturaMonto: e.toleranciaFacturaMonto,
        slaAprobacionHoras: e.slaAprobacionHoras, plazoEntregaDiasDefecto: e.plazoEntregaDiasDefecto,
        diasSeguridadDefecto: e.diasSeguridadDefecto, diasCoberturaDefecto: e.diasCoberturaDefecto,
        preciosDelegadosCompras: e.preciosDelegadosCompras,
      });
    }
  }

  /** @param campo dato de la empresa; @param valor valor escrito */
  protected cambiar<K extends keyof EmpresaCambios>(campo: K, valor: EmpresaCambios[K]): void {
    this.datos.update(d => ({ ...d, [campo]: valor }));
  }

  protected guardar(): void {
    const d = this.datos();
    if (!d.razonSocial.trim() || !d.nit.trim()) {
      this.avisos.ok('La razón social y el NIT son obligatorios.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.actualizarEmpresa({ ...d, razonSocial: d.razonSocial.trim(), nit: d.nit.trim() }).subscribe({
      next: e => {
        this.trabajando.set(false);
        this.sesion.empresa.set(e);
        this.avisos.ok('Datos de la empresa guardados.');
      },
      error: err => {
        this.trabajando.set(false);
        this.avisos.error(err);
      },
    });
  }
}
