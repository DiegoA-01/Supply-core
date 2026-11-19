import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { CentroCosto, Rol, Usuario } from '../../core/api/modelos';
import { nombreRol } from '../../core/formato/etiquetas';
import { FechaPipe } from '../../core/formato/formato';
import { ComprasService } from '../../core/servicios/compras.service';
import { UsuariosService } from '../../core/servicios/usuarios.service';
import { Avisos } from '../../core/ui/avisos';
import { PATRON_CLAVE } from '../../core/sesion/clave';
import { SesionService } from '../../core/sesion/sesion.service';

type Panel = { tipo: 'ninguno' } | { tipo: 'nuevo' } | { tipo: 'editar'; usuario: Usuario } | { tipo: 'clave'; usuario: Usuario };

/**
 * Usuarios y roles: el administrador de la empresa (ADMIN_SISTEMA) crea a sus empleados, les asigna roles y los
 * activa o desactiva; el auditor solo consulta. Los usuarios de proveedores nacen en el portal, no aquí.
 */
@Component({
  selector: 'sc-usuarios',
  imports: [FormsModule, ReactiveFormsModule, RouterLink, FechaPipe],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.scss',
})
export class Usuarios implements OnInit {
  private readonly servicio = inject(UsuariosService);
  private readonly compras = inject(ComprasService);
  private readonly avisos = inject(Avisos);
  protected readonly sesion = inject(SesionService);

  protected readonly lista = signal<Usuario[] | null>(null);
  protected readonly roles = signal<Rol[]>([]);
  protected readonly centros = signal<CentroCosto[]>([]);
  protected readonly texto = signal('');
  protected readonly rol = signal<string | null>(null);
  protected readonly activo = signal<boolean | null>(true);
  protected readonly panel = signal<Panel>({ tipo: 'ninguno' });
  protected readonly trabajando = signal(false);
  protected readonly verClave = signal(false);

  protected readonly puedeEditar = computed(() => this.sesion.tieneRol(['ADMIN_SISTEMA']) && !this.sesion.soloLectura());
  /** Roles que el administrador asigna aquí (PROVEEDOR se asigna al habilitar un proveedor). */
  protected readonly asignables = computed(() => this.roles().filter(r => r.codigo !== 'PROVEEDOR'));

  /** {@code true} si el formulario da el rol Solicitante sin centro de costo (el backend lo rechaza). */
  protected faltaCentro(): boolean {
    const v = this.form.getRawValue();
    return (v.roles ?? []).includes('SOLICITANTE') && v.centroCostoId == null;
  }
  protected readonly editando = computed(() => {
    const p = this.panel();
    return p.tipo === 'editar' ? p.usuario : null;
  });

  protected readonly form = inject(FormBuilder).nonNullable.group({
    nombreCompleto: ['', [Validators.required, Validators.maxLength(150)]],
    correo: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    password: ['', [Validators.pattern(PATRON_CLAVE)]],
    roles: [[] as string[]],
    centroCostoId: [null as number | null],
  });
  protected readonly claveNueva = signal('');

  ngOnInit(): void {
    this.servicio.roles().subscribe({ next: l => this.roles.set(l), error: () => undefined });
    this.compras.centros().subscribe({ next: l => this.centros.set(l.filter(c => c.activo)), error: () => undefined });
    this.cargar();
  }

  protected cargar(): void {
    this.lista.set(null);
    this.servicio.buscar({ texto: this.texto(), rol: this.rol(), activo: this.activo() }).subscribe({
      next: p => this.lista.set(p.contenido ?? []),
      error: e => {
        this.lista.set([]);
        this.avisos.error(e);
      },
    });
  }

  /** @param codigo rol */
  protected nombre(codigo: string): string {
    return this.roles().find(r => r.codigo === codigo)?.nombre ?? nombreRol(codigo);
  }

  /** @param id centro de costo */
  protected centro(id: number | undefined): string {
    return id ? (this.centros().find(c => c.id === id)?.nombre ?? `#${id}`) : '—';
  }

  protected nuevo(): void {
    this.form.reset({ nombreCompleto: '', correo: '', password: '', roles: [], centroCostoId: null });
    this.form.controls.password.setValidators([Validators.required, Validators.pattern(PATRON_CLAVE)]);
    this.form.controls.password.updateValueAndValidity();
    this.verClave.set(false);
    this.panel.set({ tipo: 'nuevo' });
  }

  /** @param u usuario a editar */
  protected editar(u: Usuario): void {
    this.form.reset({
      nombreCompleto: u.nombreCompleto ?? '', correo: u.correo ?? '', password: '',
      roles: [...(u.roles ?? [])], centroCostoId: u.centroCostoId ?? null,
    });
    this.form.controls.password.setValidators([Validators.pattern(PATRON_CLAVE)]);
    this.form.controls.password.updateValueAndValidity();
    this.panel.set({ tipo: 'editar', usuario: u });
  }

  /** @param u usuario al que se le pone una contraseña nueva */
  protected abrirClave(u: Usuario): void {
    this.claveNueva.set('');
    this.verClave.set(false);
    this.panel.set({ tipo: 'clave', usuario: u });
  }

  /** @param codigo rol que se marca o desmarca */
  protected alternarRol(codigo: string): void {
    const actuales = this.form.controls.roles.value;
    this.form.controls.roles.setValue(actuales.includes(codigo) ? actuales.filter(r => r !== codigo) : [...actuales, codigo]);
  }

  protected guardar(): void {
    if (this.form.invalid || !this.form.controls.roles.value.length) {
      this.form.markAllAsTouched();
      if (!this.form.controls.roles.value.length) {
        this.avisos.ok('Asigna al menos un rol.');
      }
      return;
    }
    const v = this.form.getRawValue();
    const base = { nombreCompleto: v.nombreCompleto.trim(), correo: v.correo.trim(), roles: v.roles, centroCostoId: v.centroCostoId ?? undefined };
    const u = this.editando();
    const peticion = u?.id
      ? this.servicio.actualizar(u.id, { ...base, proveedorId: u.proveedorId })
      : this.servicio.crear({ ...base, password: v.password });
    this.trabajando.set(true);
    peticion.subscribe({
      next: r => {
        this.trabajando.set(false);
        this.panel.set({ tipo: 'ninguno' });
        this.avisos.ok(u ? `${r.nombreCompleto}: cambios guardados.` : `${r.nombreCompleto} ya puede ingresar con el código de la empresa.`);
        this.cargar();
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  protected guardarClave(): void {
    const p = this.panel();
    if (p.tipo !== 'clave' || !p.usuario.id) {
      return;
    }
    if (!PATRON_CLAVE.test(this.claveNueva())) {
      this.avisos.ok('La contraseña debe tener entre 8 y 72 caracteres, con al menos una letra y un número.');
      return;
    }
    this.trabajando.set(true);
    this.servicio.restablecerPassword(p.usuario.id, this.claveNueva()).subscribe({
      next: () => {
        this.trabajando.set(false);
        this.panel.set({ tipo: 'ninguno' });
        this.avisos.ok(`Contraseña de ${p.usuario.nombreCompleto} restablecida. Entrégasela por un canal seguro.`);
      },
      error: e => {
        this.trabajando.set(false);
        this.avisos.error(e);
      },
    });
  }

  /** @param u usuario con MFA activo; el administrador reinicia su segundo factor (cierra sus sesiones) */
  protected reiniciarMfa(u: Usuario): void {
    if (!u.id || !window.confirm(`¿Reiniciar el segundo factor de ${u.nombreCompleto}? Tendrá que vincular su app de nuevo al ingresar y se cierran sus sesiones.`)) {
      return;
    }
    this.servicio.reiniciarMfa(u.id).subscribe({
      next: () => {
        this.avisos.ok(`Segundo factor de ${u.nombreCompleto} reiniciado.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }

  /** @param u usuario; activa o desactiva su acceso (su historial se conserva) */
  protected cambiarEstado(u: Usuario): void {
    if (!u.id) {
      return;
    }
    if (u.activo && !window.confirm(`¿Desactivar a ${u.nombreCompleto}? No podrá ingresar; su historial se conserva.`)) {
      return;
    }
    this.servicio.cambiarEstado(u.id, !u.activo).subscribe({
      next: r => {
        this.avisos.ok(`${r.nombreCompleto} quedó ${r.activo ? 'activo' : 'inactivo'}.`);
        this.cargar();
      },
      error: e => this.avisos.error(e),
    });
  }
}
