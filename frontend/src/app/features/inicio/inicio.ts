import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin, map, of, catchError } from 'rxjs';

import { Factura, Gasto, Producto, Proveedor, ProveedorRanking, SolicitudResumen, SugerenciaReorden } from '../../core/api/modelos';
import { CompactoPipe, PesosPipe, PuntajePipe, horasDesde } from '../../core/formato/formato';
import { ROLES_ORDENES, ROLES_PROVEEDORES, ROLES_REPORTES } from '../../core/navegacion/menu';
import { ComprasService } from '../../core/servicios/compras.service';
import { InventarioService } from '../../core/servicios/inventario.service';
import { OrdenesService } from '../../core/servicios/ordenes.service';
import { ReportesService, iso, rangoMes } from '../../core/servicios/reportes.service';
import { SelectorSede } from '../../core/ui/selector-sede';
import { Avisos } from '../../core/ui/avisos';
import { SesionService } from '../../core/sesion/sesion.service';

type Periodo = 'mes' | 'trimestre' | 'anio';

/** Pendiente que pide una acción de quien entra. */
interface Atencion {
  tipo: string;
  tono: 'ambar' | 'rojo' | 'gris';
  titulo: string;
  detalle: string;
  accion: string;
  ruta?: string;
  proveedorId?: number;
}

const ESTADOS_EN_CURSO = ['EMITIDA', 'ACEPTADA_PROVEEDOR', 'EN_PREPARACION', 'DESPACHADA', 'RECIBIDA_PARCIAL'];

/**
 * Vista 2 · Inicio. Cada tarjeta se pide solo si el rol del usuario puede verla; las cifras son las del backend.
 */
@Component({
  selector: 'sc-inicio',
  imports: [RouterLink, PesosPipe, CompactoPipe, PuntajePipe, SelectorSede],
  templateUrl: './inicio.html',
  styleUrl: './inicio.scss',
})
export class Inicio implements OnInit {
  protected readonly sesion = inject(SesionService);
  private readonly reportes = inject(ReportesService);
  private readonly compras = inject(ComprasService);
  private readonly ordenes = inject(OrdenesService);
  private readonly inventario = inject(InventarioService);
  private readonly avisos = inject(Avisos);

  protected readonly periodo = signal<Periodo>('mes');
  /** Sede de las tarjetas de gasto y del ranking; {@code null} = toda la empresa. */
  protected readonly sede = signal<number | null>(null);
  protected readonly gastoPeriodo = signal<Gasto | null>(null);
  protected readonly gastoMeses = signal<Gasto | null>(null);
  protected readonly ranking = signal<ProveedorRanking[]>([]);
  protected readonly enCurso = signal<{ total: number; despachadas: number } | null>(null);
  protected readonly porAprobar = signal<SolicitudResumen[] | null>(null);
  protected readonly bajoMinimo = signal<Producto[] | null>(null);
  protected readonly sugerencias = signal<SugerenciaReorden[] | null>(null);
  protected readonly facturasRevision = signal<Factura[]>([]);
  protected readonly proveedoresNuevos = signal<Proveedor[]>([]);

  protected readonly puedeReportes = computed(() => this.sesion.tieneRol(ROLES_REPORTES) && this.sesion.tieneModulo('REPORTES'));
  protected readonly puedeOrdenes = computed(() => this.sesion.tieneRol(ROLES_ORDENES) && this.sesion.tieneModulo('ORDENES_PAGOS'));

  protected readonly saludo = computed(() => {
    const h = new Date().getHours();
    const nombre = (this.sesion.usuario()?.nombre ?? '').split(' ')[0];
    return `${h < 12 ? 'Buen día' : h < 19 ? 'Buenas tardes' : 'Buenas noches'}, ${nombre}`;
  });
  protected readonly mesActual = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' }).format(new Date());

  /** Más de 24 h esperando decisión. */
  protected readonly demoradas = computed(() => (this.porAprobar() ?? []).filter(s => (horasDesde(s.enviadoEn) ?? 0) > 24).length);
  protected readonly sinExistencias = computed(() => (this.bajoMinimo() ?? []).filter(p => (p.stockTotal ?? 0) <= 0).length);
  protected readonly porReponer = computed(() => (this.sugerencias() ?? []).filter(s => !s.yaCubierto && (s.cantidadSugerida ?? 0) > 0).length);

  /** Barras de los últimos meses: la altura es proporcional al total que devolvió el backend (solo dibujo). */
  protected readonly barras = computed(() => {
    const g = this.gastoMeses();
    if (!g) {
      return [];
    }
    // El backend solo trae los meses con órdenes: los demás se dibujan en cero.
    const hoy = new Date();
    const meses = Array.from({ length: 6 }, (_, i) => {
      const f = new Date(hoy.getFullYear(), hoy.getMonth() - 5 + i, 1);
      const periodo = `${f.getFullYear()}-${`${f.getMonth() + 1}`.padStart(2, '0')}`;
      return { periodo, total: g.porMes?.find(m => m.periodo === periodo)?.total ?? 0 };
    });
    const max = Math.max(1, ...meses.map(m => m.total));
    return meses.map((m, i) => ({
      etiqueta: this.nombreMes(m.periodo),
      total: m.total,
      alto: Math.round((m.total / max) * 100),
      actual: i === meses.length - 1,
    }));
  });

  protected readonly categorias = computed(() => {
    const cats = [...(this.gastoPeriodo()?.porCategoria ?? [])].sort((a, b) => (b.total ?? 0) - (a.total ?? 0)).slice(0, 5);
    const max = Math.max(1, ...cats.map(c => c.total ?? 0));
    return cats.map(c => ({ nombre: c.nombre, total: c.total ?? 0, ancho: Math.round(((c.total ?? 0) / max) * 100) }));
  });

  protected readonly atencion = computed<Atencion[]>(() => [
    ...this.facturasRevision().map(f => ({
      tipo: 'Factura en revisión', tono: 'ambar' as const,
      titulo: `${f.ordenConsecutivo} · ${f.proveedorRazonSocial}`,
      detalle: `La factura ${f.numero} no cuadra con lo recibido.`,
      accion: 'Revisar', ruta: `/app/ordenes/${f.ordenId}`,
    })),
    ...(this.porAprobar() ?? []).slice(0, 3).map(s => {
      const horas = horasDesde(s.enviadoEn) ?? 0;
      const limite = this.sesion.empresa()?.slaAprobacionHoras ?? 24;
      return {
        tipo: horas >= limite * 0.8 ? 'SLA en riesgo' : 'Por aprobar', tono: horas >= limite * 0.8 ? 'rojo' as const : 'gris' as const,
        titulo: `${s.consecutivo}`,
        detalle: `Lleva ${horas} h esperando. El plazo de la empresa es de ${limite} h.`,
        accion: 'Aprobar', ruta: '/app/solicitudes',
      };
    }),
    ...this.proveedoresNuevos().map(p => ({
      tipo: 'Proveedor nuevo', tono: 'gris' as const,
      titulo: p.razonSocial ?? '', detalle: 'Se registró en el portal y espera habilitación.',
      accion: 'Habilitar', proveedorId: p.id,
    })),
  ]);

  ngOnInit(): void {
    this.cargarPeriodo();
    this.cargarMeses();

    if (this.puedeOrdenes()) {
      forkJoin(ESTADOS_EN_CURSO.map(e => this.ordenes.buscar({ estado: e, tamano: 1 }).pipe(
        map(p => ({ estado: e, total: p.totalElementos ?? 0 })), catchError(() => of({ estado: e, total: 0 })))))
        .subscribe(r => this.enCurso.set({
          total: r.reduce((s, x) => s + x.total, 0),
          despachadas: r.find(x => x.estado === 'DESPACHADA')?.total ?? 0,
        }));
    }
    if (this.sesion.tieneModulo('COMPRAS')) {
      this.compras.pendientes(50).subscribe({ next: p => this.porAprobar.set(p.contenido ?? []), error: () => this.porAprobar.set([]) });
    }
    if (this.sesion.tieneModulo('INVENTARIO')) {
      this.inventario.alertas().subscribe({ next: l => this.bajoMinimo.set(l), error: () => undefined });
    }
    if (this.sesion.tieneRol(['ADMIN_COMPRAS', 'COMPRADOR', 'ALMACENISTA', 'SOLICITANTE', 'AUDITOR']) && this.sesion.tieneModulo('COMPRAS')) {
      this.inventario.sugerencias().subscribe({ next: l => this.sugerencias.set(l), error: () => undefined });
    }
    if (this.sesion.tieneRol(['ADMINISTRATIVO', 'ADMIN_COMPRAS', 'AUDITOR']) && this.sesion.tieneModulo('ORDENES_PAGOS')) {
      this.ordenes.facturasPorEstado('EN_REVISION').subscribe({ next: p => this.facturasRevision.set(p.contenido ?? []), error: () => undefined });
    }
    if (this.sesion.tieneRol(ROLES_PROVEEDORES) && this.sesion.tieneModulo('PROVEEDORES')) {
      this.reportes.proveedores('PENDIENTE').subscribe({ next: p => this.proveedoresNuevos.set(p.contenido ?? []), error: () => undefined });
    }
  }

  /** @param id sede elegida ({@code null} = toda la empresa); recarga las tarjetas de gasto */
  protected cambiarSede(id: number | null): void {
    this.sede.set(id);
    this.cargarPeriodo();
    this.cargarMeses();
  }

  /** Gasto de los últimos seis meses para el gráfico, de la empresa o de la sede elegida. */
  private cargarMeses(): void {
    if (!this.puedeReportes()) {
      return;
    }
    const hoy = new Date();
    const desde = iso(new Date(hoy.getFullYear(), hoy.getMonth() - 5, 1));
    this.reportes.gasto(desde, rangoMes().hasta, this.sede()).subscribe({ next: g => this.gastoMeses.set(g), error: () => undefined });
  }

  /** @param p periodo de las tarjetas de gasto */
  protected cambiarPeriodo(p: Periodo): void {
    this.periodo.set(p);
    this.cargarPeriodo();
  }

  /** @param id proveedor pendiente (solo ADMIN_COMPRAS) */
  protected habilitar(id: number | undefined): void {
    if (!id) {
      return;
    }
    this.reportes.habilitar(id).subscribe({
      next: p => {
        this.proveedoresNuevos.update(l => l.filter(x => x.id !== id));
        this.avisos.ok(`${p.razonSocial} quedó habilitado y ya puede cotizar.`);
      },
      error: e => this.avisos.error(e),
    });
  }

  private cargarPeriodo(): void {
    if (!this.puedeReportes()) {
      return;
    }
    const hoy = new Date();
    const desde = this.periodo() === 'mes' ? new Date(hoy.getFullYear(), hoy.getMonth(), 1)
      : this.periodo() === 'trimestre' ? new Date(hoy.getFullYear(), Math.floor(hoy.getMonth() / 3) * 3, 1)
        : new Date(hoy.getFullYear(), 0, 1);
    const { desde: d, hasta } = { desde: iso(desde), hasta: rangoMes().hasta };
    this.reportes.gasto(d, hasta, this.sede()).subscribe({ next: g => this.gastoPeriodo.set(g), error: () => undefined });
    this.reportes.ranking(d, hasta, this.sede()).subscribe({ next: r => this.ranking.set(r.slice(0, 3)), error: () => undefined });
  }

  /** @param periodo "2026-10" */
  private nombreMes(periodo: string | undefined): string {
    if (!periodo) {
      return '';
    }
    const [a, m] = periodo.split('-').map(Number);
    const t = new Intl.DateTimeFormat('es-CO', { month: 'short' }).format(new Date(a!, (m ?? 1) - 1, 1)).replace('.', '');
    return t.charAt(0).toUpperCase() + t.slice(1);
  }
}
