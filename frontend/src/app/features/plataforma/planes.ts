import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ModuloPlataforma, Plan } from '../../core/api/modelos';
import { PlataformaService } from '../../core/servicios/plataforma.service';
import { Avisos } from '../../core/ui/avisos';

/** Plan que se está creando o editando. */
interface Borrador {
  id?: number;
  codigo: string;
  nombre: string;
  modulos: Set<string>;
  activo: boolean;
}

/**
 * Consola del dueño · Planes y módulos: qué módulos incluye cada plan que vendes. Cambiar un plan afecta a todas
 * las empresas que lo tienen; para una sola empresa se usa la excepción de módulo en la pantalla de Empresas.
 */
@Component({
  selector: 'sc-planes',
  imports: [FormsModule],
  templateUrl: './planes.html',
  styleUrl: './planes.scss',
})
export class Planes implements OnInit {
  private readonly servicio = inject(PlataformaService);
  private readonly avisos = inject(Avisos);

  protected readonly planes = signal<Plan[] | null>(null);
  protected readonly modulos = signal<ModuloPlataforma[]>([]);
  protected readonly borrador = signal<Borrador | null>(null);
  protected readonly trabajando = signal(false);

  protected readonly esNuevo = computed(() => !this.borrador()?.id);

  ngOnInit(): void {
    this.servicio.modulos().subscribe({ next: l => this.modulos.set(l), error: e => this.avisos.error(e) });
    this.cargar();
  }

  /** @param codigo módulo; nombre legible */
  protected nombreModulo(codigo: string): string {
    return this.modulos().find(m => m.codigo === codigo)?.nombre ?? codigo;
  }

  protected nuevo(): void {
    this.borrador.set({ codigo: '', nombre: '', modulos: new Set(), activo: true });
  }

  /** @param p plan a editar */
  protected editar(p: Plan): void {
    this.borrador.set({ id: p.id, codigo: p.codigo ?? '', nombre: p.nombre ?? '', modulos: new Set(p.modulos ?? []), activo: p.activo ?? true });
  }

  /** @param campo dato del plan; @param valor nuevo valor */
  protected cambiar(campo: 'codigo' | 'nombre' | 'activo', valor: string | boolean): void {
    this.borrador.update(b => (b ? { ...b, [campo]: valor } : b));
  }

  /** @param codigo módulo que se incluye o se quita */
  protected alternar(codigo: string | undefined): void {
    if (!codigo) {
      return;
    }
    this.borrador.update(b => {
      if (!b) {
        return b;
      }
      const m = new Set(b.modulos);
      if (m.has(codigo)) {
        m.delete(codigo);
      } else {
        m.add(codigo);
      }
      return { ...b, modulos: m };
    });
  }

  protected guardar(): void {
    const b = this.borrador();
    if (!b || !b.nombre.trim() || (!b.id && !/^[A-Z][A-Z0-9_]{1,29}$/.test(b.codigo.trim()))) {
      this.avisos.ok('Escribe el nombre y un código en mayúsculas sin espacios (por ejemplo PROFESIONAL).');
      return;
    }
    if (!b.modulos.size) {
      this.avisos.ok('Un plan debe incluir al menos un módulo.');
      return;
    }
    const modulos = [...b.modulos];
    const peticion = b.id
      ? this.servicio.editarPlan(b.id, { nombre: b.nombre.trim(), modulos, activo: b.activo })
      : this.servicio.crearPlan({ codigo: b.codigo.trim(), nombre: b.nombre.trim(), modulos });
    this.trabajando.set(true);
    peticion.subscribe({
      next: p => {
        this.trabajando.set(false);
        this.borrador.set(null);
        this.avisos.ok(`Plan ${p.nombre} guardado. Queda en la auditoría global.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  private cargar(): void {
    this.servicio.planes().subscribe({
      next: l => this.planes.set(l),
      error: e => {
        this.planes.set([]);
        this.avisos.error(e);
      },
    });
  }
}
