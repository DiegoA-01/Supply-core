import { NgTemplateOutlet } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { InformeCompras, InformeContenido, Pagina } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { InformesService } from '../../core/servicios/informes.service';
import { rangoMes } from '../../core/servicios/reportes.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Informe de compras. El dueño de la empresa (ADMIN_SISTEMA) lo pide y lo lee; el jefe de compras (ADMIN_COMPRAS)
 * revisa en vivo lo que se pidió, cotizó y adjudicó en el periodo, escribe su análisis y se lo envía. Lo enviado
 * queda como foto fija. La pantalla no calcula nada: muestra lo que arma el backend.
 */
@Component({
  selector: 'sc-informes',
  imports: [FormsModule, NgTemplateOutlet, RouterLink, Estado, FechaPipe, NumeroPipe, PesosPipe],
  templateUrl: './informes.html',
  styleUrl: './informes.scss',
})
export class Informes implements OnInit {
  private readonly servicio = inject(InformesService);
  private readonly avisos = inject(Avisos);
  private readonly sesion = inject(SesionService);

  protected readonly esDueno = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly esCompras = computed(() => this.sesion.tieneRol(['ADMIN_COMPRAS']) && !this.sesion.soloLectura());

  protected readonly pagina = signal<Pagina<InformeCompras> | null>(null);
  protected readonly pedidosPendientes = computed(() => (this.pagina()?.contenido ?? []).filter(i => i.estado === 'PEDIDO'));
  protected readonly abierto = signal<InformeCompras | null>(null);
  protected readonly trabajando = signal(false);

  // Pedir (dueño)
  protected readonly pedirDesde = signal(rangoMes().desde);
  protected readonly pedirHasta = signal(rangoMes().hasta);
  protected readonly mensaje = signal('');

  // Preparar y enviar (compras)
  protected readonly desde = signal(rangoMes().desde);
  protected readonly hasta = signal(rangoMes().hasta);
  protected readonly analisis = signal('');
  /** Pedido del dueño que se está respondiendo ({@code null} = informe por iniciativa propia). */
  protected readonly respondiendo = signal<InformeCompras | null>(null);
  protected readonly vista = signal<InformeContenido | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.servicio.listar().subscribe({ next: p => this.pagina.set(p), error: e => this.avisos.error(e) });
  }

  /** El dueño pide un informe del periodo. */
  protected pedir(): void {
    if (!this.pedirDesde() || !this.pedirHasta() || this.pedirHasta() < this.pedirDesde()) {
      this.avisos.ok('Revisa las fechas: la final no puede ser anterior a la inicial.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.pedir(this.pedirDesde(), this.pedirHasta(), this.mensaje().trim()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.mensaje.set('');
        this.avisos.ok('Pedido enviado: a compras le llegó el aviso.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param pedido pedido del dueño que compras va a responder: toma su periodo */
  protected responder(pedido: InformeCompras): void {
    this.respondiendo.set(pedido);
    this.desde.set(pedido.desde);
    this.hasta.set(pedido.hasta);
    this.verDatos();
  }

  protected noResponder(): void {
    this.respondiendo.set(null);
  }

  /** Trae en vivo lo que saldría en el informe del periodo. */
  protected verDatos(): void {
    if (!this.desde() || !this.hasta() || this.hasta() < this.desde()) {
      this.avisos.ok('Revisa las fechas: la final no puede ser anterior a la inicial.');
      return;
    }
    this.servicio.vistaPrevia(this.desde(), this.hasta()).subscribe({ next: c => this.vista.set(c), error: e => this.avisos.error(e) });
  }

  /** Compras envía el informe con su análisis: queda como foto fija y le llega al dueño. */
  protected enviar(): void {
    if (this.analisis().trim().length < 10) {
      this.avisos.ok('Escribe tu análisis (mínimo 10 caracteres): qué se compró, qué falta y qué recomiendas.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.enviar(this.desde(), this.hasta(), this.analisis().trim(), this.respondiendo()?.id ?? null).subscribe({
      next: i => {
        this.trabajando.set(false);
        this.analisis.set('');
        this.vista.set(null);
        this.respondiendo.set(null);
        this.abierto.set(i);
        this.avisos.ok('Informe enviado al dueño.');
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param i informe de la lista; el dueño al abrir uno enviado lo deja como leído */
  protected abrir(i: InformeCompras): void {
    if (i.estado !== 'ENVIADO') {
      if (this.esCompras()) {
        this.responder(i);
      }
      return;
    }
    const peticion = this.esDueno() && !i.leidoEn ? this.servicio.marcarLeido(i.id) : this.servicio.obtener(i.id);
    peticion.subscribe({
      next: completo => {
        this.abierto.set(completo);
        if (this.esDueno() && !i.leidoEn) {
          this.cargar();
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  protected cerrar(): void {
    this.abierto.set(null);
  }

  /** @param estados conteo por estado de solicitud */
  protected listaEstados(estados: Record<string, number> | undefined): { estado: string; total: number }[] {
    return Object.entries(estados ?? {}).map(([estado, total]) => ({ estado, total }));
  }
}
