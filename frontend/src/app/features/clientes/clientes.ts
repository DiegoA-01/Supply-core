import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { Cliente, ListaPrecio, Pagina } from '../../core/api/modelos';
import { FechaPipe, PesosPipe } from '../../core/formato/formato';
import { ClientesService } from '../../core/servicios/clientes.service';
import { VentasService } from '../../core/servicios/ventas.service';
import { SesionService } from '../../core/sesion/sesion.service';
import { Avisos, descargar } from '../../core/ui/avisos';
import { ClienteFormulario } from './cliente-formulario';

/**
 * Clientes de la empresa (spec 002): búsqueda por documento o nombre, alta y edición. El consumidor final aparece
 * primero y no se edita. Cupo, plazo y lista de precios solo los ven y cambian el dueño y el supervisor de ventas.
 */
@Component({
  selector: 'sc-clientes',
  imports: [FormsModule, RouterLink, PesosPipe, FechaPipe, ClienteFormulario],
  templateUrl: './clientes.html',
  styleUrl: './clientes.scss',
})
export class Clientes implements OnInit {
  private readonly servicio = inject(ClientesService);
  private readonly ventas = inject(VentasService);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(Avisos);

  protected readonly datos = signal<Pagina<Cliente> | null>(null);
  protected readonly texto = signal('');
  protected readonly soloActivos = signal(true);
  protected readonly pagina = signal(0);
  protected readonly listas = signal<ListaPrecio[]>([]);
  /** 'nuevo' para crear; un cliente para editarlo; null con el formulario cerrado. */
  protected readonly editando = signal<Cliente | 'nuevo' | null>(null);

  protected readonly puedeEditar = computed(() =>
    this.sesion.tieneRol(['CAJERO', 'VENDEDOR', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly puedeCondiciones = computed(() =>
    this.sesion.tieneRol(['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  protected readonly puedeExportar = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA', 'AUDITOR']));

  ngOnInit(): void {
    this.cargar();
    if (this.puedeCondiciones()) {
      this.ventas.listas().subscribe({ next: l => this.listas.set(l), error: () => undefined });
    }
  }

  protected cargar(): void {
    this.servicio.buscar(this.texto().trim() || null, this.soloActivos() ? true : null, this.pagina()).subscribe({
      next: d => this.datos.set(d),
      error: e => this.avisos.error(e),
    });
  }

  protected buscar(): void {
    this.pagina.set(0);
    this.cargar();
  }

  /** @param p página a mostrar */
  protected irA(p: number): void {
    this.pagina.set(p);
    this.cargar();
  }

  /** @param c cliente a editar (el consumidor final no se edita) */
  protected editar(c: Cliente): void {
    if (!c.esConsumidorFinal) {
      this.editando.set(c);
    }
  }

  /** @param c cliente guardado; cierra el formulario y recarga */
  protected guardado(c: Cliente): void {
    this.editando.set(null);
    this.texto.set(c.numeroDocumento ?? '');
    this.buscar();
  }

  /** @param e lo que se edita; el cliente o null si es uno nuevo */
  protected clienteDe(e: Cliente | 'nuevo'): Cliente | null {
    return e === 'nuevo' ? null : e;
  }

  protected exportar(): void {
    this.servicio.exportar().subscribe({
      next: b => descargar(b, 'clientes.xlsx'),
      error: e => this.avisos.error(e),
    });
  }
}
