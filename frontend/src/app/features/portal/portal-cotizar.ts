import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, of, throwError } from 'rxjs';

import { Cotizacion, RfqPortal } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe, NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { PortalService } from '../../core/servicios/portal.service';
import { iso } from '../../core/servicios/reportes.service';
import { Avisos } from '../../core/ui/avisos';

/** Precio e impuesto que el proveedor escribe por línea. */
interface Oferta {
  precio: number | null;
  impuesto: number;
}

/**
 * Vista 10 · Portal del proveedor: cotizar una RFQ (UC-07). El total lo calcula el backend al guardar el
 * borrador; una vez enviada, la cotización ya no se edita.
 */
@Component({
  selector: 'sc-portal-cotizar',
  imports: [FormsModule, RouterLink, Estado, PesosPipe, NumeroPipe, FechaPipe],
  templateUrl: './portal-cotizar.html',
  styleUrl: './portal-cotizar.scss',
})
export class PortalCotizar implements OnInit {
  private readonly portal = inject(PortalService);
  private readonly avisos = inject(Avisos);

  /** Id de la RFQ (de la ruta). */
  readonly rfqId = input.required<string>();

  protected readonly rfq = signal<RfqPortal | null>(null);
  protected readonly cotizacion = signal<Cotizacion | null>(null);
  protected readonly ofertas = signal<Record<number, Oferta>>({});
  protected readonly plazo = signal<number | null>(null);
  protected readonly garantia = signal<number | null>(null);
  protected readonly validez = signal(iso(new Date(Date.now() + 15 * 86_400_000)));
  protected readonly condiciones = signal('A 30 días');
  protected readonly notas = signal('');
  protected readonly trabajando = signal(false);
  /** Hay cambios sin guardar: el total mostrado puede no corresponder a los precios escritos. */
  protected readonly sinGuardar = signal(false);
  /** Nombre del archivo que se acaba de subir (solo para mostrarlo). */
  protected readonly nombreArchivo = signal<string | null>(null);
  /** La cotización ya tiene su documento adjunto: con eso basta para enviarla. */
  protected readonly tieneArchivo = computed(() => !!this.cotizacion()?.documentoOriginalId);

  protected readonly editable = computed(() => this.rfq()?.estado === 'ABIERTA' && !this.cotizacion()?.sellada && this.cotizacion()?.estado !== 'ENVIADA');
  protected readonly enviada = computed(() => this.cotizacion()?.estado === 'ENVIADA' || !!this.cotizacion()?.sellada);

  ngOnInit(): void {
    const id = Number(this.rfqId());
    forkJoin({
      rfq: this.portal.rfq(id),
      cotizacion: this.portal.cotizacion(id).pipe(catchError((e: HttpErrorResponse) => e.status === 404 ? of(null) : throwError(() => e))),
    }).subscribe({
      next: r => {
        this.rfq.set(r.rfq);
        this.aplicar(r.cotizacion);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param lineaId línea de la RFQ */
  protected oferta(lineaId: number | undefined): Oferta {
    return this.ofertas()[lineaId ?? -1] ?? { precio: null, impuesto: 19 };
  }

  /** @param lineaId línea; @param campo dato; @param valor valor escrito */
  protected cambiar(lineaId: number | undefined, campo: keyof Oferta, valor: number | null): void {
    if (lineaId === undefined) {
      return;
    }
    this.ofertas.update(o => ({ ...o, [lineaId]: { ...this.oferta(lineaId), [campo]: valor } }));
    this.sinGuardar.set(true);
  }

  protected guardar(luegoEnviar = false): void {
    const rfq = this.rfq();
    if (!rfq?.id) {
      return;
    }
    const lineas = (rfq.lineas ?? []).map(l => ({ l, o: this.oferta(l.id) })).filter(({ o }) => (o.precio ?? 0) > 0)
      .map(({ l, o }) => ({
        rfqLineaId: l.id!, cantidadOfertada: l.cantidad ?? 0, unidadOfertada: l.unidadCodigo ?? '', factorConversion: 1,
        precioUnitarioOfertado: o.precio ?? 0, impuestoPct: o.impuesto,
      }));
    if (!lineas.length) {
      if (this.tieneArchivo()) {
        // Con el archivo adjunto no hace falta escribir precios: se envía así y compras lo revisa
        if (luegoEnviar) {
          this.trabajando.set(true);
          this.enviar();
        } else {
          this.avisos.ok('Tu archivo ya quedó guardado. Puedes enviar la cotización sin escribir precios.');
        }
        return;
      }
      this.avisos.ok('Escribe el precio de al menos un producto o sube el archivo de tu cotización.');
      return;
    }
    this.trabajando.set(true);
    const condiciones = [this.condiciones().trim(), this.notas().trim()].filter(Boolean).join('. ');
    this.portal.guardar(rfq.id, {
      moneda: 'COP', plazoEntregaDias: this.plazo() ?? undefined, garantiaMeses: this.garantia() ?? undefined,
      validezHasta: this.validez() || undefined, condicionesComerciales: condiciones, lineas,
    }).subscribe({
      next: c => {
        this.aplicar(c);
        this.sinGuardar.set(false);
        if (luegoEnviar) {
          this.enviar();
        } else {
          this.trabajando.set(false);
          this.avisos.ok('Borrador guardado. El total ya está calculado.');
        }
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param evento cambio del selector de archivos */
  protected subirArchivo(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    campo.value = ''; // permite volver a elegir el mismo archivo
    const id = this.rfq()?.id;
    if (!archivo || !id) {
      return;
    }
    this.trabajando.set(true);
    this.portal.subirArchivo(id, archivo).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.aplicar(c);
        this.nombreArchivo.set(archivo.name);
        this.avisos.ok(`Archivo «${archivo.name}» adjunto. Ya puedes enviar la cotización.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private enviar(): void {
    const id = this.rfq()?.id;
    if (!id) {
      return;
    }
    this.portal.enviar(id).subscribe({
      next: c => {
        this.trabajando.set(false);
        this.aplicar(c);
        this.avisos.ok('Cotización enviada. El comprador la verá cuando cierre la fecha.');
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private aplicar(c: Cotizacion | null): void {
    this.cotizacion.set(c);
    if (!c) {
      return;
    }
    this.plazo.set(c.plazoEntregaDias ?? null);
    this.garantia.set(c.garantiaMeses ?? null);
    if (c.validezHasta) {
      this.validez.set(c.validezHasta);
    }
    if (c.condicionesComerciales) {
      this.condiciones.set(c.condicionesComerciales);
    }
    const o: Record<number, Oferta> = {};
    for (const l of c.lineas ?? []) {
      if (l.rfqLineaId) {
        o[l.rfqLineaId] = { precio: l.precioUnitarioOfertado ?? null, impuesto: l.impuestoPct ?? 19 };
      }
    }
    this.ofertas.set(o);
  }
}
