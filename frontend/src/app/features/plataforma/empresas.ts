import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { EmpresaDetalle, EmpresaPlataforma, EstadoSuscripcion, Plan } from '../../core/api/modelos';
import { Estado } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

/**
 * Vista 9 · Consola del dueño: empresas clientes, su plan, sus módulos y el estado de la suscripción.
 * No muestra datos de compra de ninguna empresa.
 */
@Component({
  selector: 'sc-empresas',
  imports: [FormsModule, ReactiveFormsModule, Estado, FechaPipe],
  templateUrl: './empresas.html',
  styleUrl: './empresas.scss',
})
export class Empresas implements OnInit {
  private readonly servicio = inject(PlataformaService);
  private readonly avisos = inject(Avisos);

  protected readonly empresas = signal<EmpresaPlataforma[] | null>(null);
  protected readonly detalle = signal<EmpresaDetalle | null>(null);
  protected readonly cambios = signal<Record<string, boolean>>({});
  protected readonly planes = signal<Plan[]>([]);
  protected readonly creando = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly busqueda = signal('');
  /** Plan elegido en el selector, antes de confirmar el cambio. */
  protected readonly planElegido = signal<string | null>(null);
  /** Muestra la contraseña inicial del administrador para revisarla antes de crear la empresa. */
  protected readonly verClave = signal(false);

  protected readonly filtradas = computed(() => {
    const t = this.busqueda().trim().toLowerCase();
    return (this.empresas() ?? []).filter(e => !t || [e.razonSocial, e.nit, e.codigoPublico].some(x => x?.toLowerCase().includes(t)));
  });
  protected readonly conteo = computed(() => {
    const l = this.empresas() ?? [];
    const de = (e: EstadoSuscripcion) => l.filter(x => x.estado === e).length;
    return { activas: de('ACTIVA'), prueba: de('PRUEBA'), mora: de('MOROSA'), suspendidas: de('SUSPENDIDA') };
  });
  protected readonly hayCambios = computed(() => Object.keys(this.cambios()).length > 0);

  protected readonly nueva = inject(FormBuilder).nonNullable.group({
    razonSocial: ['', Validators.required],
    nit: ['', Validators.required],
    // texto libre (espacios, mayúsculas, tildes); solo se excluye lo que rompe la dirección del registro: / \ ? # %
    codigoPublico: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(40), Validators.pattern(/^[^/\\?#%\u0000-\u001f]+$/)]],
    planCodigo: ['', Validators.required],
    estadoInicial: ['PRUEBA' as EstadoSuscripcion],
    adminNombre: ['', Validators.required],
    adminCorreo: ['', [Validators.required, Validators.email]],
    adminPassword: ['', [Validators.required, Validators.minLength(8)]],
  });

  ngOnInit(): void {
    this.cargar();
    this.servicio.planes().subscribe({ next: l => this.planes.set(l.filter(p => p.activo)), error: () => undefined });
  }

  /** @param e empresa de la tabla */
  protected abrir(e: EmpresaPlataforma): void {
    this.cambios.set({});
    this.planElegido.set(null);
    this.servicio.empresa(e.id!).subscribe({ next: d => this.detalle.set(d), error: err => this.avisos.error(err) });
  }

  /** @param codigo módulo; estado que se verá (con el cambio pendiente, si hay) */
  protected activo(codigo: string, actual: boolean | undefined): boolean {
    return this.cambios()[codigo] ?? !!actual;
  }

  /** @param codigo módulo; @param actual estado guardado */
  protected alternar(codigo: string, actual: boolean | undefined): void {
    this.cambios.update(c => {
      const nuevo = { ...c };
      const destino = !(c[codigo] ?? !!actual);
      if (destino === !!actual) {
        delete nuevo[codigo];
      } else {
        nuevo[codigo] = destino;
      }
      return nuevo;
    });
  }

  protected guardarModulos(): void {
    const d = this.detalle();
    const id = d?.empresa?.id;
    const pendientes = Object.entries(this.cambios());
    if (!id || !pendientes.length) {
      return;
    }
    this.trabajando.set(true);
    let ultimo: EmpresaDetalle | null = null;
    const siguiente = (i: number): void => {
      if (i >= pendientes.length) {
        this.trabajando.set(false);
        this.cambios.set({});
        if (ultimo) {
          this.detalle.set(ultimo);
        }
        this.avisos.ok('Módulos guardados. Queda en la auditoría global.');
        return;
      }
      const [modulo, habilitado] = pendientes[i]!;
      this.servicio.modulo(id, modulo, habilitado).subscribe({
        next: r => {
          ultimo = r;
          siguiente(i + 1);
        },
        error: e => {
          this.trabajando.set(false);
          this.avisos.error(e);
        },
      });
    };
    siguiente(0);
  }

  /** Cambia el plan de la empresa abierta: sus módulos pasan a ser los del plan nuevo (más sus excepciones). */
  protected cambiarPlan(): void {
    const id = this.detalle()?.empresa?.id;
    const plan = this.planElegido();
    if (!id || !plan) {
      return;
    }
    this.trabajando.set(true);
    this.servicio.cambiarPlan(id, plan).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.detalle.set(d);
        this.planElegido.set(null);
        this.avisos.ok(`${d.empresa?.razonSocial} pasó al plan ${d.empresa?.planNombre ?? plan}.`);
        this.cargar();
      },
      error: err => {
        this.trabajando.set(false);
        this.avisos.error(err);
      },
    });
  }

  /** @param estado nuevo estado de la suscripción */
  protected cambiarEstado(estado: EstadoSuscripcion): void {
    const e = this.detalle()?.empresa;
    if (!e?.id) {
      return;
    }
    const motivo = window.prompt(estado === 'SUSPENDIDA' ? 'Motivo de la suspensión (corta el acceso de todos sus usuarios):' : 'Motivo del cambio:');
    if (!motivo?.trim()) {
      return;
    }
    this.servicio.estado(e.id, estado, motivo.trim()).subscribe({
      next: d => {
        this.detalle.set(d);
        this.avisos.ok(`${d.empresa?.razonSocial}: ${estado === 'SUSPENDIDA' ? 'suspendida' : 'actualizada'}.`);
        this.cargar();
      },
      error: err => this.avisos.error(err),
    });
  }

  protected crear(): void {
    if (this.nueva.invalid) {
      this.nueva.markAllAsTouched();
      return;
    }
    this.trabajando.set(true);
    this.servicio.crear(this.nueva.getRawValue()).subscribe({
      next: d => {
        this.trabajando.set(false);
        this.creando.set(false);
        this.nueva.reset();
        this.detalle.set(d);
        this.avisos.ok(`Empresa ${d.empresa?.razonSocial} creada con su administrador.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.servicio.empresas().subscribe({
      next: l => {
        this.empresas.set(l);
        if (!this.detalle() && l.length) {
          this.abrir(l[0]!);
        }
      },
      error: e => {
        this.empresas.set([]);
        this.avisos.error(e);
      },
    });
  }
}
