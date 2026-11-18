import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Bodega, OfertaContrato, SolicitudResumen } from '../../core/api/modelos';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { ComprasService } from '../../core/servicios/compras.service';
import { CotizacionesService } from '../../core/servicios/cotizaciones.service';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Orden directa: cuando hay un contrato vigente con precios pactados que cubre los productos de una solicitud aprobada,
 * el comprador emite la orden sin cotizar. El backend revisa la cobertura, el tope y lo aprobado, y calcula los totales;
 * esta pantalla solo muestra cada contrato posible y deja emitir el que cumple todo.
 */
@Component({
  selector: 'sc-orden-directa',
  imports: [FormsModule, RouterLink, FechaPipe, NumeroPipe, PesosPipe],
  templateUrl: './orden-directa.html',
  styleUrl: './orden-directa.scss',
})
export class OrdenDirecta implements OnInit {
  private readonly compras = inject(ComprasService);
  private readonly ordenes = inject(OrdenesService);
  private readonly cotizaciones = inject(CotizacionesService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);

  protected readonly solicitudes = signal<SolicitudResumen[] | null>(null);
  protected readonly solicitudId = signal<number | null>(null);
  protected readonly ofertas = signal<OfertaContrato[] | null>(null);
  protected readonly elegido = signal<number | null>(null);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly bodegaId = signal<number | null>(null);
  protected readonly lugar = signal('');
  protected readonly fecha = signal('');
  protected readonly buscando = signal(false);
  protected readonly trabajando = signal(false);

  protected readonly oferta = computed(() => this.ofertas()?.find(o => o.contratoId === this.elegido()) ?? null);

  ngOnInit(): void {
    this.compras.buscar({ estado: 'APROBADA' }).subscribe({
      next: r => this.solicitudes.set(r.contenido ?? []),
      error: e => {
        this.solicitudes.set([]);
        this.avisos.error(e);
      },
    });
    this.cotizaciones.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    const entrega = new Date();
    entrega.setDate(entrega.getDate() + 3);
    this.fecha.set(iso(entrega));
  }

  /** @param id solicitud aprobada elegida; busca los contratos que la cubren */
  protected elegirSolicitud(id: number | null): void {
    this.solicitudId.set(id);
    this.ofertas.set(null);
    this.elegido.set(null);
    if (!id) {
      return;
    }
    this.buscando.set(true);
    this.ordenes.ofertasContrato(id).subscribe({
      next: l => {
        this.buscando.set(false);
        this.ofertas.set(l);
        const primera = l.find(o => o.puedeEmitirse);
        this.elegido.set(primera?.contratoId ?? null);
      },
      error: e => {
        this.buscando.set(false);
        this.ofertas.set([]);
        this.avisos.error(e);
      },
    });
  }

  /** @param o oferta; por qué no se puede emitir, en una frase */
  protected motivoNo(o: OfertaContrato): string | null {
    if (o.puedeEmitirse) {
      return null;
    }
    if (!o.cubreTodo) {
      return `No cubre: ${(o.productosSinCubrir ?? []).join(', ')}`;
    }
    if (o.dentroDelTope === false) {
      return 'Se pasa del tope del contrato';
    }
    if (o.dentroDeLoAprobado === false) {
      return 'Supera lo aprobado en la solicitud';
    }
    return 'No se puede emitir';
  }

  protected emitir(): void {
    if (!this.solicitudId() || !this.oferta()?.puedeEmitirse) {
      this.avisos.ok('Elige un contrato que se pueda emitir.');
      return;
    }
    if (!this.bodegaId() || !this.lugar().trim() || !this.fecha()) {
      this.avisos.ok('Completa bodega, lugar y fecha de entrega.');
      return;
    }
    this.trabajando.set(true);
    this.ordenes.emitirDirecta({
      solicitudId: this.solicitudId()!, contratoId: this.elegido()!, bodegaDestinoId: this.bodegaId()!,
      lugarEntrega: this.lugar().trim(), fechaEntregaEsperada: this.fecha(),
    }).subscribe({
      next: o => {
        this.avisos.ok(`Orden ${o.consecutivo} emitida al precio pactado y enviada al proveedor.`);
        void this.router.navigate(['/app/ordenes', o.id]);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param id bodega elegida; si el lugar está vacío se propone su nombre */
  protected elegirBodega(id: number | null): void {
    this.bodegaId.set(id);
    if (!this.lugar().trim()) {
      this.lugar.set(this.bodegas().find(b => b.id === id)?.nombre ?? '');
    }
  }
}
