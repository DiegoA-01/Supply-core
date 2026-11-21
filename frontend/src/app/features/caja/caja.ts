import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Bodega, Caja, MedioPago, Pagina, Turno } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';

/** Nombre de cada medio de pago. */
export const MEDIOS: Record<MedioPago, string> = {
  EFECTIVO: 'Efectivo', TARJETA_DEBITO: 'Tarjeta débito', TARJETA_CREDITO: 'Tarjeta crédito',
  TRANSFERENCIA: 'Transferencia', BILLETERA: 'Billetera digital',
};

/** Caja en edición. */
interface EdicionCaja {
  id: number | null;
  nombre: string;
  bodegaId: number | null;
  prefijo: string;
  tope: number | null;
  activa: boolean;
}

/**
 * Cajas y turnos (spec 003), para supervisión, el dueño y auditoría: turnos con su tiquete Z, diferencias por aprobar,
 * configuración de cajas y de la tolerancia, y el PIN de supervisor. Esperados y diferencias los calcula el backend.
 */
@Component({
  selector: 'sc-caja',
  imports: [FormsModule, RouterLink, PesosPipe, FechaPipe],
  templateUrl: './caja.html',
  styleUrl: './caja.scss',
})
export class CajasTurnos implements OnInit {
  private readonly servicio = inject(CajaService);
  private readonly inventario = inject(InventarioService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly medios = MEDIOS;
  protected readonly ordenMedios = Object.keys(MEDIOS) as MedioPago[];
  protected readonly pestana = signal<'turnos' | 'cajas' | 'pin'>('turnos');
  protected readonly turnos = signal<Pagina<Turno> | null>(null);
  protected readonly soloPendientes = signal(true);
  protected readonly detalle = signal<Turno | null>(null);
  protected readonly explicacion = signal('');
  protected readonly cajas = signal<Caja[]>([]);
  protected readonly bodegas = signal<Bodega[]>([]);
  protected readonly edicion = signal<EdicionCaja | null>(null);
  protected readonly tolerancia = signal<number | null>(null);
  /** Bodega para lo devuelto en mal estado (spec 006); null = no reingresa. */
  protected readonly averias = signal<number | null>(null);
  /** Anulaciones y descuentos autorizados por cajero en un día que avisan al dueño (spec 010). */
  protected readonly umbralAnulaciones = signal<number | null>(null);
  protected readonly umbralDescuentos = signal<number | null>(null);
  protected readonly tienePin = signal<boolean | null>(null);
  protected readonly passwordActual = signal('');
  protected readonly pinNuevo = signal('');
  protected readonly guardando = signal(false);

  protected readonly esDueno = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly puedeAprobar = computed(() =>
    this.sesion.tieneRol(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly sedeDeBodega = computed(() => {
    const e = this.edicion();
    return this.bodegas().find(b => b.id === e?.bodegaId)?.sedeNombre ?? '—';
  });

  ngOnInit(): void {
    this.cargarTurnos();
    this.servicio.cajas().subscribe({ next: l => this.cajas.set(l), error: e => this.avisos.error(e) });
    this.servicio.configuracion().subscribe({
      next: c => {
        this.tolerancia.set(c.toleranciaDiferencia);
        this.averias.set(c.bodegaAveriasId ?? null);
        this.umbralAnulaciones.set(c.umbralAnulacionesDia ?? null);
        this.umbralDescuentos.set(c.umbralDescuentosDia ?? null);
      },
      error: () => undefined,
    });
    if (this.puedeAprobar()) {
      this.servicio.tienePin().subscribe({ next: r => this.tienePin.set(r.tienePin), error: () => undefined });
    }
  }

  protected cargarTurnos(pagina = 0): void {
    this.servicio.turnos({ pendientes: this.soloPendientes(), pagina }).subscribe({
      next: p => this.turnos.set(p),
      error: e => this.avisos.error(e),
    });
  }

  /** @param t turno; abre su detalle (tiquete Z si está cerrado) */
  protected ver(t: Turno): void {
    this.explicacion.set('');
    this.servicio.turno(t.id!).subscribe({ next: d => this.detalle.set(d), error: e => this.avisos.error(e) });
  }

  protected aprobar(): void {
    const d = this.detalle();
    const texto = this.explicacion().trim();
    if (!d || texto.length < 10) {
      this.avisos.ok('Escribe qué pasó con la diferencia (al menos 10 caracteres).');
      return;
    }
    this.servicio.aprobar(d.id!, texto).subscribe({
      next: t => {
        this.detalle.set(t);
        this.avisos.ok('Diferencia aprobada.');
        this.cargarTurnos();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param c caja a editar, o null para una nueva */
  protected editarCaja(c: Caja | null): void {
    if (this.bodegas().length === 0) {
      this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    }
    this.edicion.set(c
      ? { id: c.id!, nombre: c.nombre ?? '', bodegaId: c.bodegaId ?? null, prefijo: c.prefijoConsecutivo ?? '', tope: c.topeEfectivo ?? 0, activa: c.activa ?? true }
      : { id: null, nombre: '', bodegaId: null, prefijo: '', tope: 0, activa: true });
  }

  /** @param campo dato; @param valor valor escrito */
  protected cambiar<K extends keyof EdicionCaja>(campo: K, valor: EdicionCaja[K]): void {
    this.edicion.update(e => (e ? { ...e, [campo]: valor } : e));
  }

  protected guardarCaja(): void {
    const e = this.edicion();
    const bodega = this.bodegas().find(b => b.id === e?.bodegaId);
    if (!e || !bodega || !e.nombre.trim() || !e.prefijo.trim() || e.tope === null || e.tope < 0) {
      this.avisos.ok('Completa nombre, bodega, prefijo y tope de efectivo.');
      return;
    }
    const datos = { nombre: e.nombre.trim(), sedeId: bodega.sedeId!, bodegaId: bodega.id!, prefijoConsecutivo: e.prefijo.trim(), topeEfectivo: e.tope, activa: e.activa };
    this.guardando.set(true);
    const peticion = e.id ? this.servicio.editarCaja(e.id, datos) : this.servicio.crearCaja(datos);
    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.edicion.set(null);
        this.avisos.ok('Caja guardada.');
        this.servicio.cajas().subscribe({ next: l => this.cajas.set(l), error: () => undefined });
      },
      error: err => {
        this.guardando.set(false);
        this.avisos.error(err);
      },
    });
  }

  protected guardarTolerancia(): void {
    const t = this.tolerancia();
    if (t === null || t < 0) {
      return;
    }
    this.servicio.configurar(t, this.averias(), { anulaciones: this.umbralAnulaciones(), descuentos: this.umbralDescuentos() }).subscribe({
      next: c => {
        this.tolerancia.set(c.toleranciaDiferencia);
        this.averias.set(c.bodegaAveriasId ?? null);
        this.umbralAnulaciones.set(c.umbralAnulacionesDia ?? null);
        this.umbralDescuentos.set(c.umbralDescuentosDia ?? null);
        this.avisos.ok('Configuración guardada.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** Trae las bodegas la primera vez que se abre la lista de bodegas de averías. */
  protected cargarBodegas(): void {
    if (this.bodegas().length === 0) {
      this.inventario.bodegas().subscribe({ next: l => this.bodegas.set(l), error: () => undefined });
    }
  }

  protected guardarPin(): void {
    if (!/^\d{6}$/.test(this.pinNuevo()) || !this.passwordActual()) {
      this.avisos.ok('Escribe tu contraseña y un PIN de 6 dígitos.');
      return;
    }
    const password = this.passwordActual();
    const pin = this.pinNuevo();
    this.passwordActual.set('');
    this.pinNuevo.set('');
    this.servicio.cambiarPin(password, pin).subscribe({
      next: () => {
        this.tienePin.set(true);
        this.avisos.ok('PIN guardado. Úsalo para autorizar en el mostrador.');
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param valor diferencia; tono del número */
  protected tono(valor: number | null | undefined): string {
    return valor == null || valor === 0 ? '' : valor < 0 ? 'rojo' : 'ambar';
  }

  /** @param mapa valores por medio; @param medio medio */
  protected valor(mapa: Record<string, number> | undefined, medio: MedioPago): number {
    return mapa?.[medio] ?? 0;
  }
}
