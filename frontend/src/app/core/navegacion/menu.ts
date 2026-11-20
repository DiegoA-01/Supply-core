/** Opción del menú lateral. Se muestra si el usuario tiene el rol; si el módulo está apagado sale bloqueada. */
export interface OpcionMenu {
  texto: string;
  icono: string;
  ruta: string;
  roles?: string[];
  modulo?: string;
  /** {@code true} si solo se marca activa en su ruta exacta (porque otras opciones cuelgan de ella). */
  exacta?: boolean;
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
/** Roles que leen pagos (mismos que {@code GET /api/pagos}). */
export const ROLES_PAGOS = ['ADMINISTRATIVO', 'ADMIN_COMPRAS', 'AUDITOR'];
/** Roles que leen despachos (mismos que {@code GET /api/despachos}). */
export const ROLES_DESPACHOS = ['ALMACENISTA', 'ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'];
/** Roles que ven la rotación de productos (mismos que {@code GET /api/reportes/rotacion}). */
export const ROLES_ROTACION = ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO', 'AUDITOR', 'ALMACENISTA'];
/** Roles que leen reportes de gasto y proveedores. */
/** Roles que ven el plan de reabastecimiento (mismos que {@code GET /api/reabastecimiento}). */
export const ROLES_REABASTECIMIENTO = ['ADMIN_COMPRAS', 'COMPRADOR', 'ALMACENISTA', 'SOLICITANTE', 'AUDITOR', 'ADMIN_SISTEMA'];
/** Roles que ven los catálogos de proveedores y las oportunidades (mismos que {@code /api/catalogos}). */
export const ROLES_CATALOGOS = ['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR', 'ADMIN_SISTEMA'];
/** Roles que ven precios, costos y margen (mismos que {@code /api/ventas}). */
export const ROLES_PRECIOS = ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'];
/** Roles que consultan clientes (mismos que {@code GET /api/clientes}). */
export const ROLES_CLIENTES = ['CAJERO', 'VENDEDOR', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'];
/** Roles que usan el punto de venta: cobra el cajero o el supervisor; el vendedor arma pedidos. */
export const ROLES_POS = ['CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR'];
/** Roles que abren y cierran su propio turno de caja (mismos que {@code /api/turnos}). */
export const ROLES_MI_TURNO = ['CAJERO', 'SUPERVISOR_VENTAS'];
/** Roles que entran a Ventas (mismos que {@code GET /api/ventas}; el cajero solo busca por consecutivo para anular o devolver). */
export const ROLES_VENTAS = ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR', 'VENDEDOR', 'CAJERO'];
/** Roles que ven los pedidos de venta (mismos que {@code /api/pedidos-venta}). */
export const ROLES_PEDIDOS_VENTA = ['VENDEDOR', 'CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA'];
/** Roles que ven la cartera (mismos que {@code GET /api/cartera}). */
export const ROLES_CARTERA = ['CAJERO', 'SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'];
/** Roles que ven cajas y turnos (mismos que {@code GET /api/turnos}). */
export const ROLES_CAJA = ['SUPERVISOR_VENTAS', 'ADMIN_SISTEMA', 'AUDITOR'];
/** Roles que ven el tablero del dueño (mismos que {@code GET /api/reportes/tablero}). */
export const ROLES_TABLERO = ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR'];
/** Roles que leen los reportes de gasto. */
export const ROLES_REPORTES = ['AUDITOR', 'ADMIN_COMPRAS', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'];
/** Roles que leen proveedores. */
export const ROLES_PROVEEDORES = ['ADMIN_COMPRAS', 'COMPRADOR', 'AUDITOR'];
/** Roles internos (todos menos PROVEEDOR). */
export const INTERNOS = ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'SOLICITANTE', 'COMPRADOR', 'APROBADOR', 'ALMACENISTA', 'CAJERO', 'SUPERVISOR_VENTAS', 'VENDEDOR', 'ADMINISTRATIVO', 'AUDITOR'];

/** Menú de la empresa, en el orden del mockup. Los roles y módulos son los mismos que exige el backend. */
export const MENU: SeccionMenu[] = [
  {
    titulo: 'Compras',
    opciones: [
      { texto: 'Inicio', icono: 'home', ruta: '/app/inicio' },
      { texto: 'Solicitudes', icono: 'description', ruta: '/app/solicitudes', modulo: 'COMPRAS', contador: 'solicitudes' },
      { texto: 'Cotizaciones con IA', icono: 'auto_awesome', ruta: '/app/cotizaciones', roles: ROLES_RFQ, modulo: 'RFQ_COTIZACIONES' },
      { texto: 'Adjudicaciones', icono: 'fact_check', ruta: '/app/adjudicaciones', roles: INTERNOS, modulo: 'RFQ_COTIZACIONES' },
      { texto: 'Pedidos internos', icono: 'campaign', ruta: '/app/pedidos', roles: INTERNOS, modulo: 'COMPRAS' },
      { texto: 'Órdenes y pagos', icono: 'receipt_long', ruta: '/app/ordenes', roles: ROLES_ORDENES, modulo: 'ORDENES_PAGOS' },
      { texto: 'Recepción', icono: 'local_shipping', ruta: '/app/recepcion', roles: ROLES_ORDENES, modulo: 'RECEPCION' },
      { texto: 'Pagos', icono: 'payments', ruta: '/app/pagos', roles: ROLES_PAGOS, modulo: 'ORDENES_PAGOS' },
    ],
  },
  {
    titulo: 'Inventario',
    opciones: [
      { texto: 'Productos y stock', icono: 'inventory_2', ruta: '/app/inventario', modulo: 'INVENTARIO', contador: 'bajoMinimo', exacta: true },
      { texto: 'Plan de reabastecimiento', icono: 'event_repeat', ruta: '/app/inventario/reabastecimiento', roles: ROLES_REABASTECIMIENTO, modulo: 'COMPRAS' },
      { texto: 'Rotación de productos', icono: 'trending_up', ruta: '/app/inventario/rotacion', roles: ROLES_ROTACION, modulo: 'REPORTES' },
      { texto: 'Despachos', icono: 'outbox', ruta: '/app/despachos', roles: ROLES_DESPACHOS, modulo: 'DESPACHOS' },
      { texto: 'Movimientos de stock', icono: 'swap_horiz', ruta: '/app/inventario/movimientos', modulo: 'INVENTARIO' },
      { texto: 'Proveedores', icono: 'group', ruta: '/app/proveedores', roles: ROLES_PROVEEDORES, modulo: 'PROVEEDORES' },
      { texto: 'Catálogos de proveedores', icono: 'storefront', ruta: '/app/catalogos', roles: ROLES_CATALOGOS, modulo: 'PROVEEDORES' },
    ],
  },
  {
    titulo: 'Ventas',
    opciones: [
      { texto: 'Punto de venta', icono: 'shopping_cart_checkout', ruta: '/app/pos', roles: ROLES_POS, modulo: 'VENTAS' },
      { texto: 'Mi turno', icono: 'schedule', ruta: '/app/caja/mi-turno', roles: ROLES_MI_TURNO, modulo: 'CAJA' },
      { texto: 'Pedidos de venta', icono: 'receipt_long', ruta: '/app/pedidos-venta', roles: ROLES_PEDIDOS_VENTA, modulo: 'VENTAS' },
      { texto: 'Ventas', icono: 'point_of_sale', ruta: '/app/ventas', roles: ROLES_VENTAS, modulo: 'VENTAS', exacta: true },
      { texto: 'Reportes de ventas', icono: 'query_stats', ruta: '/app/ventas/reportes', roles: ROLES_VENTAS, modulo: 'VENTAS' },
      { texto: 'Clientes', icono: 'groups', ruta: '/app/clientes', roles: ROLES_CLIENTES, modulo: 'VENTAS' },
      { texto: 'Cartera', icono: 'account_balance_wallet', ruta: '/app/cartera', roles: ROLES_CARTERA, modulo: 'CARTERA' },
      { texto: 'Cajas y turnos', icono: 'storefront', ruta: '/app/caja', roles: ROLES_CAJA, modulo: 'CAJA', exacta: true },
    ],
  },
  {
    titulo: 'Gestión',
    opciones: [
      { texto: 'Tablero del dueño', icono: 'monitoring', ruta: '/app/tablero', roles: ROLES_TABLERO, modulo: 'REPORTES' },
      { texto: 'Precios y margen', icono: 'sell', ruta: '/app/precios', roles: ROLES_PRECIOS, modulo: 'VENTAS' },
      { texto: 'Reportes', icono: 'bar_chart', ruta: '/app/reportes', roles: ROLES_REPORTES, modulo: 'REPORTES' },
      { texto: 'Informe de compras', icono: 'summarize', ruta: '/app/informes', roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS'], modulo: 'COMPRAS' },
      { texto: 'Delegaciones', icono: 'group_add', ruta: '/app/delegaciones', roles: INTERNOS, modulo: 'COMPRAS' },
      { texto: 'Auditoría', icono: 'shield', ruta: '/app/auditoria', roles: ['AUDITOR', 'ADMIN_SISTEMA'], modulo: 'AUDITORIA' },
      { texto: 'Usuarios y roles', icono: 'apartment', ruta: '/app/usuarios', roles: ['ADMIN_SISTEMA', 'AUDITOR'] },
      { texto: 'Configuración', icono: 'settings', ruta: '/app/configuracion', roles: ['ADMIN_SISTEMA', 'ADMIN_COMPRAS', 'AUDITOR', 'COMPRADOR', 'APROBADOR', 'ADMINISTRATIVO'], exacta: true },
      { texto: 'Avisos y correos', icono: 'notifications', ruta: '/app/configuracion/notificaciones', roles: ['ADMIN_SISTEMA'] },
    ],
  },
];
