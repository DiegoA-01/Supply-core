import { Component, ElementRef, HostListener, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import {
  Caja, Cliente, CotizacionVenta, LineaVentaRequest, MedioPagoVenta, PagoVentaRequest, ProductoParaVender, Turno, Venta,
} from '../../core/api/modelos';
import { NumeroPipe, PesosPipe } from '../../core/formato/formato';
import { CajaService } from '../../core/servicios/caja.service';
import { ClientesService } from '../../core/servicios/clientes.service';
import { VentaService } from '../../core/servicios/venta.service';
import { VentasService } from '../../core/servicios/ventas.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos } from '../../core/ui/avisos';
import { AutorizacionPin } from '../caja/autorizacion-pin';
import { ClienteFormulario } from '../clientes/cliente-formulario';
import { imprimirTiquete } from './tiquete';

/** Producto en el carrito: solo qué, cuánto y qué descuento se pide. El precio lo pone el servidor. */
export interface ItemCarrito {
  productoId: number;
  codigo: string;
  nombre: string;
  unidadMedidaId: number | null;
  unidad: string;
  cantidad: number;
  descuentoPct: number;
  stock: number | null;
}

/** Pago que escribe el cajero. */
export interface PagoEscrito {
  medio: MedioPagoVenta;
  monto: number | null;
  ultimos4: string;
  referencia: string;
}

/** Texto de cada medio de pago. Crédito y saldo a favor solo para clientes registrados (spec 007). */
export const MEDIOS_VENTA: Record<MedioPagoVenta, string> = {
  EFECTIVO: 'Efectivo', TARJETA_DEBITO: 'Tarjeta débito', TARJETA_CREDITO: 'Tarjeta crédito',
  TRANSFERENCIA: 'Transferencia', BILLETERA: 'Billetera digital', CREDITO: 'Crédito del cliente', SALDO_FAVOR: 'Saldo a favor',
};

/** Acción que espera el PIN del supervisor. */
type PinPendiente = 'DESCUENTO' | 'PRECIO_BAJO_MINIMO' | null;

/**
 * Punto de venta (spec 005). El campo del lector tiene el foco: Enter agrega el producto. Atajos: F2 buscar, F4
 * cliente, F9 cobrar, Esc cancelar. La pantalla nunca calcula dinero: cada cambio pide al servidor los totales y el
 * cambio. La clave de idempotencia nace con el carrito y se reenvía igual si el cobro falla por la red. El vendedor
 * (sin caja) usa la misma pantalla para guardar un pedido de venta que luego cobra el cajero.
 */
@Component({
  selector: 'sc-pos',
  imports: [FormsModule, RouterLink, PesosPipe, NumeroPipe, AutorizacionPin, ClienteFormulario],
  templateUrl: './pos.html',
  styleUrl: './pos.scss',
})
export class PuntoDeVenta implements OnInit, OnDestroy {
  private readonly ventas = inject(VentaService);
  private readonly catalogo = inject(VentasService);
  private readonly caja = inject(CajaService);
  private readonly clientes = inject(ClientesService);
  private readonly avisos = inject(Avisos);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  protected readonly sesion = inject(SesionService);

  private readonly lector = viewChild<ElementRef<HTMLInputElement>>('lector');
  private temporizador: ReturnType<typeof setTimeout> | null = null;
  private pedidoPendiente: number | null = null;
  /** Número de la última cotización pedida: una respuesta de una anterior llega tarde y se ignora. */
  private consulta = 0;

  protected readonly medios = MEDIOS_VENTA;
  /** Medios que se ofrecen: el crédito (con módulo de cartera) y el saldo a favor solo con un cliente registrado. */
  protected readonly ordenMedios = computed(() => (Object.keys(MEDIOS_VENTA) as MedioPagoVenta[]).filter(m =>
    (m !== 'CREDITO' || (!!this.cliente() && this.sesion.tieneModulo('CARTERA'))) && (m !== 'SALDO_FAVOR' || !!this.cliente())));

  /** Cobra quien tiene caja; el vendedor solo arma pedidos. */
  protected readonly cobra = computed(() => this.sesion.tieneRol(['CAJERO', 'SUPERVISOR_VENTAS']) && !this.sesion.soloLectura());
  protected readonly turno = signal<Turno | null>(null);
  protected readonly bodegaId = signal<number | null>(null);
  protected readonly listo = signal(false);

  protected readonly texto = signal('');
  protected readonly opciones = signal<ProductoParaVender[]>([]);
  protected readonly carrito = signal<ItemCarrito[]>([]);
  protected readonly cliente = signal<Cliente | null>(null);
  protected readonly consumidorFinal = signal<Cliente | null>(null);
  protected readonly pedidoVentaId = signal<number | null>(null);
  protected readonly pedidoConsecutivo = signal<string | null>(null);
  protected readonly cotizacion = signal<CotizacionVenta | null>(null);
  protected readonly calculando = signal(false);

  protected readonly panelCliente = signal(false);
  protected readonly buscarCliente = signal('');
  protected readonly clientesEncontrados = signal<Cliente[]>([]);
  protected readonly creandoCliente = signal(false);

  protected readonly cobrando = signal(false);
  protected readonly pagos = signal<PagoEscrito[]>([]);
  protected readonly pin = signal<PinPendiente>(null);
  protected readonly tokenDescuento = signal<string | null>(null);
  protected readonly tokenPrecio = signal<string | null>(null);
  protected readonly enviando = signal(false);
  protected readonly clave = signal(this.nuevaClave());
  protected readonly reintento = signal(false);
  protected readonly ultima = signal<Venta | null>(null);

  protected readonly vacio = computed(() => this.carrito().length === 0);
  /**
   * Con la venta en camino o pendiente de reintento el carrito no se toca: si cambiara, el reintento con la misma clave
   * devolvería la venta anterior y lo agregado saldría sin pagar.
   */
  protected readonly bloqueado = computed(() => this.enviando() || this.reintento());
  /** El cobro solo se confirma cuando el servidor dice que los pagos cuadran. */
  protected readonly puedeConfirmar = computed(() => {
    const c = this.cotizacion();
    return !!c && c.cambio != null && !c.mensajePago && !this.enviando() && !this.calculando();
  });

  ngOnDestroy(): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
      this.temporizador = null;
    }
    this.consulta++;
  }

  ngOnInit(): void {
    this.pedidoPendiente = Number(this.ruta.snapshot.queryParamMap.get('pedido')) || null;
    this.clientes.consumidorFinal().subscribe({ next: c => this.consumidorFinal.set(c), error: () => undefined });
    if (!this.cobra()) {
      this.listo.set(true);
      this.cargarPedidoPendiente();
      return;
    }
    this.caja.actual().subscribe({
      next: t => {
        this.turno.set(t);
        this.caja.cajas(true).subscribe({
          next: cajas => this.bodegaId.set(cajas.find((c: Caja) => c.id === t.cajaId)?.bodegaId ?? null),
          error: () => undefined,
        });
        this.listo.set(true);
        this.cargarPedidoPendiente();
        this.enfocar();
      },
      error: (e: HttpErrorResponse) => {
        if (e.status === 404) {
          this.avisos.ok('Abra su turno de caja antes de vender.');
          void this.router.navigate(['/app/caja/mi-turno']);
          return;
        }
        this.avisos.error(e);
      },
    });
  }

  /** Atajos de teclado del mostrador. */
  @HostListener('document:keydown', ['$event'])
  protected atajo(e: KeyboardEvent): void {
    if (e.key === 'F2') {
      e.preventDefault();
      this.enfocar();
    } else if (e.key === 'F4') {
      e.preventDefault();
      this.abrirClientes();
    } else if (e.key === 'F9') {
      e.preventDefault();
      if (this.cobrando()) {
        this.confirmar();
      } else {
        this.abrirCobro();
      }
    } else if (e.key === 'Escape') {
      this.cancelar(PuntoDeVenta.enCampo(e));
    }
  }

  /** @param e tecla @returns si se escribía en un campo (Esc ahí no debe vaciar el carrito) */
  private static enCampo(e: KeyboardEvent): boolean {
    const destino = e.target as HTMLElement | null;
    return !!destino && ['INPUT', 'SELECT', 'TEXTAREA'].includes(destino.tagName);
  }

  /** @returns {@code true} (y avisa) si el carrito no se puede cambiar ahora */
  private fijo(): boolean {
    if (this.bloqueado()) {
      this.avisos.ok('La venta está en camino: confírmela o descártela antes de cambiar el carrito.');
      return true;
    }
    return false;
  }

  // ------------------------------------------------------------- productos

  /** Enter en el lector: si hay una sola coincidencia (o una exacta por código) se agrega; si no, se listan. */
  protected leer(): void {
    const q = this.texto().trim();
    if (!q) {
      return;
    }
    this.catalogo.buscarParaVender(q, this.listaId(), this.bodegaId()).subscribe({
      next: lista => {
        const exacto = lista.find(p => p.codigo?.toLowerCase() === q.toLowerCase() || p.codigoBarras === q);
        if (exacto || lista.length === 1) {
          this.agregar(exacto ?? lista[0]);
        } else if (lista.length === 0) {
          this.avisos.ok(`No hay productos para «${q}».`);
          this.texto.set('');
        } else {
          this.opciones.set(lista);
        }
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param p producto elegido; si ya está en el carrito suma una unidad */
  protected agregar(p: ProductoParaVender): void {
    if (this.fijo()) {
      return;
    }
    if (p.precio == null) {
      this.avisos.ok(`${p.nombre} no tiene precio de venta.`);
      return;
    }
    const items = this.carrito();
    const i = items.findIndex(x => x.productoId === p.productoId && x.unidadMedidaId === p.unidadMedidaId);
    if (i >= 0) {
      this.carrito.set(items.map((x, j) => (j === i ? { ...x, cantidad: x.cantidad + 1 } : x)));
    } else {
      this.carrito.set([...items, {
        productoId: p.productoId!, codigo: p.codigo ?? '', nombre: p.nombre ?? '', unidadMedidaId: p.unidadMedidaId ?? null,
        unidad: p.unidad ?? '', cantidad: 1, descuentoPct: 0, stock: p.stock ?? null,
      }]);
    }
    this.texto.set('');
    this.opciones.set([]);
    this.recalcular();
    this.enfocar();
  }

  /** @param i posición; @param cantidad cantidad nueva (0 o menos quita la línea) */
  protected cambiarCantidad(i: number, cantidad: number | null): void {
    if (this.fijo()) {
      return;
    }
    if (cantidad == null || cantidad <= 0) {
      this.quitar(i);
      return;
    }
    this.carrito.update(items => items.map((x, j) => (j === i ? { ...x, cantidad } : x)));
    this.recalcular();
  }

  /** @param i posición; @param pct descuento pedido */
  protected cambiarDescuento(i: number, pct: number | null): void {
    if (this.fijo()) {
      return;
    }
    const valor = Math.min(100, Math.max(0, pct ?? 0));
    this.carrito.update(items => items.map((x, j) => (j === i ? { ...x, descuentoPct: valor } : x)));
    this.tokenDescuento.set(null);
    this.tokenPrecio.set(null);
    this.recalcular();
  }

  /** @param i posición */
  protected quitar(i: number): void {
    if (this.fijo()) {
      return;
    }
    this.carrito.update(items => items.filter((_, j) => j !== i));
    this.recalcular();
    this.enfocar();
  }

  /** @param i posición @returns la línea calculada por el servidor */
  protected calculada(i: number) {
    return this.cotizacion()?.lineas?.[i] ?? null;
  }

  // ------------------------------------------------------------- cliente

  protected abrirClientes(): void {
    this.panelCliente.set(true);
    this.creandoCliente.set(false);
    this.buscarCliente.set('');
    this.clientesEncontrados.set([]);
  }

  protected buscarClientes(): void {
    const q = this.buscarCliente().trim();
    if (!q) {
      return;
    }
    this.clientes.buscar(q, true).subscribe({ next: p => this.clientesEncontrados.set(p.contenido ?? []), error: e => this.avisos.error(e) });
  }

  /** @param c cliente elegido o recién creado */
  protected elegirCliente(c: Cliente | null): void {
    if (this.fijo()) {
      this.panelCliente.set(false);
      return;
    }
    this.cliente.set(c && !c.esConsumidorFinal ? c : null);
    this.panelCliente.set(false);
    this.creandoCliente.set(false);
    this.recalcular();
    this.enfocar();
  }

  // ------------------------------------------------------------- cobro

  protected abrirCobro(): void {
    if (this.vacio() || this.cobrando() || this.bloqueado()) {
      return;
    }
    if (!this.cobra()) {
      this.guardarPedido();
      return;
    }
    const total = this.cotizacion()?.totalAPagar ?? null;
    this.pagos.set([{ medio: 'EFECTIVO', monto: total, ultimos4: '', referencia: '' }]);
    this.cobrando.set(true);
    this.recalcular(true);
  }

  protected agregarPago(): void {
    if (this.fijo()) {
      return;
    }
    this.pagos.update(p => [...p, { medio: 'TARJETA_DEBITO', monto: null, ultimos4: '', referencia: '' }]);
  }

  /** @param i pago; @param campo campo; @param valor valor nuevo */
  protected cambiarPago<K extends keyof PagoEscrito>(i: number, campo: K, valor: PagoEscrito[K]): void {
    if (this.fijo()) {
      return;
    }
    this.pagos.update(p => p.map((x, j) => (j === i ? { ...x, [campo]: valor } : x)));
    this.recalcular(true);
  }

  /** @param i pago */
  protected quitarPago(i: number): void {
    if (this.fijo()) {
      return;
    }
    this.pagos.update(p => p.filter((_, j) => j !== i));
    this.recalcular(true);
  }

  /** @param m medio @returns texto */
  protected textoMedio(m: MedioPagoVenta): string {
    return (MEDIOS_VENTA as Record<string, string>)[m] ?? m;
  }

  /** @param p pago @returns si es tarjeta (pide últimos 4 y autorización) */
  protected esTarjeta(p: PagoEscrito): boolean {
    return p.medio === 'TARJETA_DEBITO' || p.medio === 'TARJETA_CREDITO';
  }

  /** Confirma la venta; antes pide el PIN si el servidor dijo que hace falta. */
  protected confirmar(): void {
    const c = this.cotizacion();
    if (!c || !this.puedeConfirmar()) {
      return;
    }
    if (c.requiereAutorizacionDescuento && !this.tokenDescuento()) {
      this.pin.set('DESCUENTO');
      return;
    }
    if (c.requiereAutorizacionPrecio && !this.tokenPrecio()) {
      this.pin.set('PRECIO_BAJO_MINIMO');
      return;
    }
    this.enviando.set(true);
    this.ventas.confirmar({
      clienteId: this.cliente()?.id, pedidoVentaId: this.pedidoVentaId() ?? undefined, lineas: this.lineas(), pagos: this.pagosEnviados(),
      autorizacionDescuento: this.tokenDescuento() ?? undefined, autorizacionPrecio: this.tokenPrecio() ?? undefined,
    }, this.clave()).subscribe({
      next: v => {
        this.enviando.set(false);
        this.ultima.set(v);
        this.avisos.ok(`Venta ${v.consecutivo} cobrada${(v.cambio ?? 0) > 0 ? `: cambio ${v.cambio}` : ''}.`);
        this.imprimir(v.id!);
        this.limpiar();
        this.caja.actual().subscribe({ next: t => this.turno.set(t), error: () => undefined });
      },
      error: (e: HttpErrorResponse) => {
        this.enviando.set(false);
        if (e.status === 0) {
          // La red falló: no se sabe si se cobró. Se reintenta con la MISMA clave y el servidor no cobra dos veces.
          this.reintento.set(true);
        }
        const codigo = (e.error as { codigo?: string } | null)?.codigo;
        if (codigo === 'AUTORIZACION_INVALIDA' || codigo === 'AUTORIZACION_REQUERIDA') {
          this.tokenDescuento.set(null);
          this.tokenPrecio.set(null);
        }
        this.avisos.error(e);
      },
    });
  }

  /** @param token permiso de un solo uso del supervisor */
  protected autorizado(token: string): void {
    if (this.pin() === 'DESCUENTO') {
      this.tokenDescuento.set(token);
    } else {
      this.tokenPrecio.set(token);
    }
    this.pin.set(null);
    this.confirmar();
  }

  /** @param id venta; pide el tiquete e imprime (la reimpresión sale como COPIA) */
  protected imprimir(id: number): void {
    this.ventas.tiquete(id).subscribe({
      next: t => {
        imprimirTiquete(t);
        // el marco de impresión se lleva el foco: se devuelve al lector
        setTimeout(() => this.enfocar(), 300);
      },
      error: e => this.avisos.error(e),
    });
  }

  /** Vendedor: guarda el carrito como pedido de venta para que lo cobre el cajero. */
  protected guardarPedido(): void {
    if (this.vacio() || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.ventas.crearPedido({ clienteId: this.cliente()?.id, lineas: this.lineas() }).subscribe({
      next: p => {
        this.enviando.set(false);
        this.avisos.ok(`Pedido ${p.consecutivo} guardado: el cajero lo cobra desde Pedidos de venta.`);
        this.limpiar();
      },
      error: e => {
        this.enviando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /**
   * Esc: cierra el panel abierto. Sin panel abierto vacía el carrito, pero solo fuera de un campo y después de
   * preguntar; con la venta en camino o por reintentar no hace nada (para eso está «Descartar»).
   *
   * @param enCampo si la tecla salió de un campo de texto
   */
  protected cancelar(enCampo = false): void {
    if (this.bloqueado() && !this.pin()) {
      return;
    }
    if (this.pin()) {
      this.pin.set(null);
    } else if (this.panelCliente()) {
      this.panelCliente.set(false);
    } else if (this.cobrando()) {
      this.cobrando.set(false);
      this.recalcular();
    } else if (this.opciones().length) {
      this.opciones.set([]);
    } else if (!this.vacio() && !enCampo && confirm('¿Vaciar el carrito?')) {
      this.limpiar();
    }
    this.enfocar();
  }

  /**
   * Abandona una venta que quedó sin respuesta por la red. Antes de cobrar otra vez hay que revisar el historial: si
   * la primera sí llegó, ya está cobrada.
   */
  protected descartar(): void {
    if (this.enviando()) {
      return;
    }
    if (confirm('La venta pudo haberse cobrado. Revise el historial de ventas antes de cobrarla otra vez. ¿Descartar el carrito?')) {
      this.limpiar();
    }
  }

  // ------------------------------------------------------------- apoyo

  /** Pide al servidor los totales (y el cambio si se está cobrando), con una pausa corta entre teclas. */
  protected recalcular(conPagos = this.cobrando()): void {
    if (this.temporizador) {
      clearTimeout(this.temporizador);
    }
    if (this.vacio()) {
      this.consulta++;
      this.calculando.set(false);
      this.cotizacion.set(null);
      return;
    }
    this.calculando.set(true);
    const numero = ++this.consulta;
    this.temporizador = setTimeout(() => {
      this.temporizador = null;
      this.ventas.cotizar(this.cliente()?.id ?? null, this.lineas(), conPagos ? this.pagosEnviados() : []).subscribe({
        next: c => {
          if (numero !== this.consulta) {
            return; // llegó tarde: ya se pidió otra con datos más nuevos
          }
          this.calculando.set(false);
          this.cotizacion.set(c);
        },
        error: e => {
          if (numero !== this.consulta) {
            return;
          }
          this.calculando.set(false);
          this.avisos.error(e);
        },
      });
    }, 250);
  }

  /** @returns el carrito como lo espera el servidor */
  private lineas(): LineaVentaRequest[] {
    return this.carrito().map(x => ({ productoId: x.productoId, unidadMedidaId: x.unidadMedidaId ?? undefined, cantidad: x.cantidad, descuentoPct: x.descuentoPct }));
  }

  /** @returns los pagos con monto, con los campos de tarjeta solo cuando aplican */
  private pagosEnviados(): PagoVentaRequest[] {
    return this.pagos().filter(p => (p.monto ?? 0) > 0).map(p => ({
      medio: p.medio, monto: p.monto!,
      ultimos4: this.esTarjeta(p) && p.ultimos4 ? p.ultimos4 : undefined,
      referencia: p.referencia.trim() || undefined,
    }));
  }

  /** @returns lista de precios del cliente (null = General) */
  private listaId(): number | null {
    return this.cliente()?.listaPrecioId ?? null;
  }

  /** Carrito nuevo: otra clave de idempotencia, sin pagos ni permisos. */
  private limpiar(): void {
    this.carrito.set([]);
    this.cotizacion.set(null);
    this.cliente.set(null);
    this.pagos.set([]);
    this.cobrando.set(false);
    this.tokenDescuento.set(null);
    this.tokenPrecio.set(null);
    this.pedidoVentaId.set(null);
    this.pedidoConsecutivo.set(null);
    this.reintento.set(false);
    this.clave.set(this.nuevaClave());
    this.enfocar();
  }

  /** Carga en el carrito el pedido que llegó por la dirección ({@code ?pedido=id}). */
  private cargarPedidoPendiente(): void {
    const id = this.pedidoPendiente;
    this.pedidoPendiente = null;
    if (!id || !this.cobra()) {
      return;
    }
    this.ventas.pedido(id).subscribe({
      next: p => {
        if (p.estado !== 'PENDIENTE_COBRO') {
          this.avisos.ok(`El pedido ${p.consecutivo} ya no está pendiente.`);
          return;
        }
        this.carrito.set((p.lineas ?? []).map(l => ({
          productoId: l.productoId!, codigo: l.codigo ?? '', nombre: l.nombre ?? '', unidadMedidaId: l.unidadMedidaId ?? null,
          unidad: l.unidad ?? '', cantidad: l.cantidad ?? 1, descuentoPct: l.descuentoPct ?? 0, stock: null,
        })));
        this.pedidoVentaId.set(p.id!);
        this.pedidoConsecutivo.set(p.consecutivo ?? null);
        if (p.clienteId && p.clienteId !== this.consumidorFinal()?.id) {
          this.cliente.set({ id: p.clienteId, nombre: p.cliente } as Cliente);
        }
        this.recalcular();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** Devuelve el foco al campo del lector. */
  protected enfocar(): void {
    setTimeout(() => this.lector()?.nativeElement.focus());
  }

  /** @returns clave nueva para un carrito */
  private nuevaClave(): string {
    return crypto.randomUUID();
  }
}
