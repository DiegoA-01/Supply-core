/** Opción del menú lateral. Se muestra si el usuario tiene el rol; si el módulo está apagado sale bloqueada. */
export interface OpcionMenu {
  texto: string;
  icono: string;
  ruta: string;
  roles?: string[];
  modulo?: string;
  /** Clave del contador que se muestra a la derecha. */
  contador?: 'solicitudes' | 'bajoMinimo';
}

export interface SeccionMenu {
  titulo: string;
  opciones: OpcionMenu[];
}

/** Roles que leen órdenes (mismos que {@code GET /api/ordenes}). */
export const ROLES_ORDENES = ['COMPRADOR', 'ADMIN_COMPRAS', 'ALMACENISTA', 'ADMINISTRATIVO', 'APROBADOR', 'AUDITOR'];
/** Roles que ven RFQ y análisis (mismos que {@code GET /api/rfqs}). */
export const ROLES_RFQ = ['COMPRADOR', 'ADMIN_COMPRAS', 'APROBADOR', 'AUDITOR'];
/** Roles que leen reportes de gasto y proveedores. */
export const ROLES_REPORTES = ['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'];
/** Roles que leen proveedores. */
export const ROLES_PROVEEDORES = ['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'];
/** Roles internos (todos menos PROVEEDOR). */
export const INTERNOS = ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'ADMINISTRATIVO', 'AUDITOR'];

/** Menú de la empresa, en el orden del mockup. Los roles y módulos son los mismos que exige el backend. */
export const MENU: SeccionMenu[] = [
  {
    titulo: 'Compras',
    opciones: [
      { texto: 'Inicio', icono: 'home', ruta: '/app/inicio' },
      { texto: 'Solicitudes', icono: 'description', ruta: '/app/solicitudes', modulo: 'COMPRAS', contador: 'solicitudes' },
      { texto: 'Cotizaciones con IA', icono: 'auto_awesome', ruta: '/app/cotizaciones', roles: ROLES_RFQ, modulo: 'RFQ_COTIZACIONES' },
      { texto: 'Órdenes y pagos', icono: 'receipt_long', ruta: '/app/ordenes', roles: ROLES_ORDENES, modulo: 'ORDENES_PAGOS' },
      { texto: 'Recepción', icono: 'local_shipping', ruta: '/app/recepcion', roles: ROLES_ORDENES, modulo: 'RECEPCION' },
    ],
  },
  {
    titulo: 'Inventario',
    opciones: [
      { texto: 'Productos y stock', icono: 'inventory_2', ruta: '/app/inventario', modulo: 'INVENTARIO', contador: 'bajoMinimo' },
      { texto: 'Proveedores', icono: 'group', ruta: '/app/proveedores', roles: ROLES_PROVEEDORES, modulo: 'PROVEEDORES' },
    ],
  },
  {
    titulo: 'Gestión',
    opciones: [
      { texto: 'Reportes', icono: 'bar_chart', ruta: '/app/reportes', roles: ROLES_REPORTES, modulo: 'REPORTES' },
      { texto: 'Auditoría', icono: 'shield', ruta: '/app/auditoria', roles: ['AUDITOR', 'ADMIN_SISTEMA'], modulo: 'AUDITORIA' },
      { texto: 'Usuarios y roles', icono: 'apartment', ruta: '/app/usuarios', roles: ['ADMIN_SISTEMA', 'AUDITOR'] },
    ],
  },
];
