import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ConfigNotificacion } from '../../core/api/modelos';
import { NotificacionesService } from '../../core/servicios/notificaciones.service';
import { Avisos } from '../../core/ui/avisos';

/** Nombre legible de cada evento que genera un aviso. */
const EVENTOS: Record<string, string> = {
  PROVEEDOR_PENDIENTE: 'Un proveedor se registró y espera habilitación',
  STOCK_BAJO_MINIMO: 'Un producto quedó bajo su mínimo',
  REABASTECIMIENTO_DIARIO: 'Resumen de la mañana: productos que hay que pedir hoy',
  STOCK_BAJO_SEDE: 'Una sede se quedó corta de un producto',
  PEDIDO_INTERNO: 'Alguien de la empresa necesita un producto',
  PEDIDO_INTERNO_RESPONDIDO: 'Compras respondió tu pedido interno',
  CATALOGO_ACTUALIZADO: 'Un proveedor actualizó su catálogo',
  PRECIO_BAJO_COSTO: 'Se vendió por debajo del costo',
  CREDITO_BLOQUEADO: 'Se bloqueó el crédito de un cliente en mora',
  ALERTA_CAJERO: 'Un cajero llegó al umbral de anulaciones o descuentos',
  CUADRE_DESCUADRE: 'El cuadre diario encontró ventas, inventario o caja que no cuadran',
  SOLICITUD_PENDIENTE_APROBACION: 'Hay una solicitud esperando una decisión',
  SOLICITUD_DECIDIDA: 'Decidieron una solicitud',
  SOLICITUD_APROBADA: 'Una solicitud quedó aprobada y lista para cotizar',
  RFQ_INVITACION: 'Un proveedor recibe invitación a cotizar',
  RFQ_CERRADA: 'Se cerró una RFQ',
  RFQ_CANCELADA: 'Se canceló una RFQ',
  COTIZACION_RECIBIDA: 'Llegó una cotización',
  EXTRACCION_REQUIERE_REVISION: 'La IA leyó un archivo y hay datos por confirmar',
  ADJUDICACION_PENDIENTE_APROBACION: 'Una adjudicación espera aprobación',
  ADJUDICACION_DECIDIDA: 'Decidieron una adjudicación',
  COTIZACION_SELECCIONADA: 'Una cotización del proveedor fue seleccionada',
  ORDEN_EMITIDA: 'Se emitió una orden',
  ORDEN_ACTUALIZADA: 'Una orden tiene una versión nueva',
  ORDEN_RESPUESTA_PROVEEDOR: 'El proveedor aceptó o rechazó una orden',
  ORDEN_DESPACHADA: 'El proveedor despachó una orden',
  PAGO_REGISTRADO: 'Se registró un pago',
  PAGO_ANULADO: 'Se anuló un pago',
  RECEPCION_REGISTRADA: 'Se registró una recepción',
  RECEPCION_EXCEDENTE: 'Llegó más mercancía de la pedida',
  RECEPCION_CERRADA: 'Se cerró la recepción de una orden',
  FACTURA_EN_REVISION: 'Una factura no cuadra y está en revisión',
  FACTURA_DECIDIDA: 'Decidieron una factura en revisión',
  SOLICITUD_SLA_POR_VENCER: 'Una solicitud está por vencer su plazo de aprobación',
  SOLICITUD_ESCALADA: 'Una solicitud se escaló por plazo vencido',
  DELEGACION_ASIGNADA: 'Alguien delegó aprobaciones a otra persona',
};

/** Una fila: un evento con sus dos canales. */
interface FilaEvento {
  evento: string;
  nombre: string;
  correo?: ConfigNotificacion;
  campana?: ConfigNotificacion;
}

/**
 * Avisos y correos: el administrador de la empresa decide qué eventos generan correo y cuáles llegan a la campana.
 * Es la configuración de toda la empresa; cada cambio se guarda al tocar el interruptor.
 */
@Component({
  selector: 'sc-notificaciones-config',
  imports: [RouterLink],
  templateUrl: './notificaciones-config.html',
  styleUrl: './notificaciones-config.scss',
})
export class NotificacionesConfig implements OnInit {
  private readonly servicio = inject(NotificacionesService);
  private readonly avisos = inject(Avisos);

  protected readonly lista = signal<ConfigNotificacion[] | null>(null);
  /** Evento y canal que se está guardando ({@code EVENTO/CANAL}). */
  protected readonly guardando = signal<string | null>(null);

  protected readonly filas = computed<FilaEvento[]>(() => {
    const porEvento = new Map<string, FilaEvento>();
    for (const c of this.lista() ?? []) {
      const e = c.evento ?? '';
      const fila = porEvento.get(e) ?? { evento: e, nombre: EVENTOS[e] ?? e };
      if (c.canal === 'EMAIL') {
        fila.correo = c;
      } else {
        fila.campana = c;
      }
      porEvento.set(e, fila);
    }
    return [...porEvento.values()];
  });

  ngOnInit(): void {
    this.servicio.configuracion().subscribe({
      next: l => this.lista.set(l),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }

  /** @param c canal de un evento; lo activa o desactiva y deja la lista con lo que guardó el servidor */
  protected alternar(c: ConfigNotificacion | undefined): void {
    if (!c?.evento || !c.canal || this.guardando() !== null) {
      return;
    }
    this.guardando.set(`${c.evento}/${c.canal}`);
    // por evento y canal: los avisos que la empresa nunca cambió no tienen id todavía
    this.servicio.cambiarPorEvento(c.evento, c.canal, !c.activo).subscribe({
      next: r => {
        this.guardando.set(null);
        this.lista.update(l => (l ?? []).map(x => (x.evento === r.evento && x.canal === r.canal ? r : x)));
      },
      error: e => {
        this.guardando.set(null);
        this.avisos.error(e);
      },
    });
  }
}
