/** Tipo de entrada de cada campo del formulario de un catálogo. */
export type TipoCampo = 'texto' | 'numero' | 'seleccion' | 'sede';

/** Un campo del formulario de un catálogo (y, si va en `columnas`, también una columna de la tabla). */
export interface CampoCatalogo {
  /** Nombre del campo en la API. */
  clave: string;
  etiqueta: string;
  tipo: TipoCampo;
  requerido?: boolean;
  /** Largo máximo (texto). */
  max?: number;
  /** Opciones de una selección. */
  opciones?: { valor: string; texto: string }[];
  ayuda?: string;
  /** Campo de la respuesta que se muestra en la tabla si es distinto de la clave (por ejemplo el nombre de la sede). */
  mostrar?: string;
  /** Si es {@code false} el campo no se puede cambiar al editar. */
  editable?: boolean;
}

/** Cómo se arma la pantalla de un catálogo simple. */
export interface ConfigCatalogo {
  clave: string;
  titulo: string;
  /** Nombre en singular para "Nueva …". */
  singular: string;
  descripcion: string;
  ruta: string;
  icono: string;
  campos: CampoCatalogo[];
  /** Roles que pueden crear, editar, activar y desactivar. */
  roles: string[];
  /** Texto que explica qué pasa al desactivar. */
  aviso?: string;
}

/** Los cinco catálogos simples de la empresa, con los mismos roles que exige el backend. */
export const CATALOGOS: ConfigCatalogo[] = [
  {
    clave: 'sedes', titulo: 'Sedes', singular: 'sede', icono: 'storefront', ruta: '/api/sedes', roles: ['ADMIN_SISTEMA'],
    descripcion: 'Los locales o puntos de la empresa. Cada bodega pertenece a una sede.',
    campos: [
      { clave: 'nombre', etiqueta: 'Nombre', tipo: 'texto', requerido: true, max: 150 },
      { clave: 'direccion', etiqueta: 'Dirección', tipo: 'texto', max: 250 },
      { clave: 'latitud', etiqueta: 'Latitud', tipo: 'numero', ayuda: 'Opcional. Va junto con la longitud.' },
      { clave: 'longitud', etiqueta: 'Longitud', tipo: 'numero', ayuda: 'Opcional. Va junto con la latitud.' },
    ],
  },
  {
    clave: 'centros-costo', titulo: 'Centros de costo', singular: 'centro de costo', icono: 'account_tree', ruta: '/api/centros-costo', roles: ['ADMIN_SISTEMA'],
    descripcion: 'Las áreas a las que se cargan las compras. El presupuesto se define por centro de costo.',
    campos: [
      { clave: 'codigo', etiqueta: 'Código', tipo: 'texto', requerido: true, max: 30 },
      { clave: 'nombre', etiqueta: 'Nombre', tipo: 'texto', requerido: true, max: 150 },
    ],
  },
  {
    clave: 'bodegas', titulo: 'Bodegas', singular: 'bodega', icono: 'warehouse', ruta: '/api/bodegas', roles: ['ADMIN_SISTEMA'],
    descripcion: 'Donde se guarda el stock. Las recepciones, los despachos y los traslados se hacen entre bodegas.',
    campos: [
      { clave: 'nombre', etiqueta: 'Nombre', tipo: 'texto', requerido: true, max: 150 },
      { clave: 'direccion', etiqueta: 'Dirección', tipo: 'texto', max: 250 },
      { clave: 'sedeId', etiqueta: 'Sede', tipo: 'sede', mostrar: 'sedeNombre' },
    ],
    aviso: 'No se puede desactivar una bodega que todavía tiene stock: primero traslada o despacha lo que tenga.',
  },
  {
    clave: 'categorias', titulo: 'Categorías', singular: 'categoría', icono: 'category', ruta: '/api/categorias', roles: ['ADMIN_COMPRAS', 'ADMIN_SISTEMA'],
    descripcion: 'Agrupan los productos y los proveedores. Se invita a cotizar a los proveedores de la categoría de cada producto.',
    campos: [{ clave: 'nombre', etiqueta: 'Nombre', tipo: 'texto', requerido: true, max: 150 }],
  },
  {
    clave: 'unidades-medida', titulo: 'Unidades de medida', singular: 'unidad de medida', icono: 'straighten', ruta: '/api/unidades-medida', roles: ['ADMIN_COMPRAS', 'ADMIN_SISTEMA'],
    descripcion: 'Cómo se cuenta cada producto. Las de una misma magnitud se convierten entre sí con su factor.',
    campos: [
      { clave: 'codigo', etiqueta: 'Código', tipo: 'texto', requerido: true, max: 20, editable: false },
      { clave: 'nombre', etiqueta: 'Nombre', tipo: 'texto', requerido: true, max: 100 },
      { clave: 'magnitud', etiqueta: 'Magnitud', tipo: 'seleccion', requerido: true, editable: false,
        opciones: [{ valor: 'CANTIDAD', texto: 'Cantidad' }, { valor: 'MASA', texto: 'Masa' }, { valor: 'VOLUMEN', texto: 'Volumen' }, { valor: 'LONGITUD', texto: 'Longitud' }] },
      { clave: 'factorConversionBase', etiqueta: 'Factor frente a la unidad base', tipo: 'numero', requerido: true, editable: false,
        ayuda: 'Cuántas unidades base de su magnitud vale una de estas. La unidad base tiene factor 1 (si la base es el kilo, el gramo vale 0.001).' },
    ],
    aviso: 'Después de crear una unidad solo se puede cambiar su nombre: el código, la magnitud y el factor ya los usan el stock y las conversiones.',
  },
];
