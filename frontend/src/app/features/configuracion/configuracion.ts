import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SesionService } from '../../core/sesion/sesion.service';
import { CATALOGOS } from './catalogos';

/** Una tarjeta del índice de configuración. */
interface Entrada {
  titulo: string;
  descripcion: string;
  icono: string;
  ruta: string;
  /** Roles que pueden cambiarlo; los demás lo ven en modo consulta. */
  edita: string[];
  /** Módulo que debe tener activo la empresa. */
  modulo?: string;
}

/**
 * Índice de configuración: todo lo que define cómo trabaja la empresa (catálogos, reglas de aprobación, presupuestos,
 * contratos, avisos). Cada tarjeta dice si el usuario puede cambiarlo o solo consultarlo.
 */
@Component({
  selector: 'sc-configuracion',
  imports: [RouterLink],
  templateUrl: './configuracion.html',
  styleUrl: './configuracion.scss',
})
export class Configuracion {
  protected readonly sesion = inject(SesionService);

  protected readonly grupos: { titulo: string; entradas: Entrada[] }[] = [
    {
      titulo: 'Empresa',
      entradas: [
        { titulo: 'Datos y reglas de la empresa', descripcion: 'Razón social, NIT, tope de anticipos, plazo para aprobar y tolerancia de factura.', icono: 'domain', ruta: '/app/configuracion/empresa', edita: ['ADMIN_SISTEMA'] },
      ],
    },
    {
      titulo: 'Catálogos',
      entradas: [
        ...CATALOGOS.map(c => ({ titulo: c.titulo, descripcion: c.descripcion, icono: c.icono, ruta: `/app/configuracion/${c.clave}`, edita: c.roles })),
        { titulo: 'Productos', descripcion: 'El catálogo de lo que se compra y se guarda: precios de referencia, mínimos y máximos.', icono: 'inventory_2', ruta: '/app/inventario', edita: ['ADMIN_COMPRAS'], modulo: 'INVENTARIO' },
        { titulo: 'Proveedores', descripcion: 'Habilitar, suspender y corregir los datos de quienes cotizan.', icono: 'group', ruta: '/app/proveedores', edita: ['ADMIN_COMPRAS'], modulo: 'PROVEEDORES' },
        { titulo: 'Usuarios y roles', descripcion: 'Quién entra a la empresa y qué puede hacer.', icono: 'apartment', ruta: '/app/usuarios', edita: ['ADMIN_SISTEMA'] },
      ],
    },
    {
      titulo: 'Reglas de compra',
      entradas: [
        { titulo: 'Políticas de aprobación', descripcion: 'Qué rol aprueba una solicitud o una adjudicación según el monto.', icono: 'rule', ruta: '/app/configuracion/politicas', edita: ['ADMIN_COMPRAS'], modulo: 'COMPRAS' },
        { titulo: 'Pesos de evaluación', descripcion: 'Qué tanto cuenta el precio, el plazo y la garantía al comparar cotizaciones.', icono: 'tune', ruta: '/app/configuracion/pesos', edita: ['ADMIN_COMPRAS'], modulo: 'RFQ_COTIZACIONES' },
        { titulo: 'Presupuestos', descripcion: 'Cuánto puede gastar cada centro de costo cada mes.', icono: 'savings', ruta: '/app/configuracion/presupuestos', edita: ['ADMIN_COMPRAS'], modulo: 'COMPRAS' },
        { titulo: 'Contratos', descripcion: 'Precios pactados con proveedores para emitir órdenes directas sin cotizar.', icono: 'handshake', ruta: '/app/configuracion/contratos', edita: ['ADMIN_COMPRAS'], modulo: 'COMPRAS' },
        { titulo: 'Delegaciones', descripcion: 'Deja a otra persona aprobar por ti durante unas fechas.', icono: 'group_add', ruta: '/app/delegaciones', edita: ['ADMIN_COMPRAS', 'APROBADOR'], modulo: 'COMPRAS' },
      ],
    },
    {
      titulo: 'Avisos',
      entradas: [
        { titulo: 'Avisos y correos', descripcion: 'Qué eventos generan un correo y cuáles llegan a la campana.', icono: 'notifications', ruta: '/app/configuracion/notificaciones', edita: ['ADMIN_SISTEMA'] },
      ],
    },
  ];

  /** Solo las tarjetas del módulo activo, para no mostrar lo que la empresa no tiene contratado. */
  protected readonly visibles = computed(() => this.grupos.map(g => ({ ...g, entradas: g.entradas.filter(e => this.sesion.tieneModulo(e.modulo)) })).filter(g => g.entradas.length));

  /** @param e entrada; {@code true} si el usuario puede cambiarla */
  protected puedeEditar(e: Entrada): boolean {
    return this.sesion.tieneRol(e.edita) && !this.sesion.soloLectura();
  }
}
