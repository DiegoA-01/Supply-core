# Plan 001

## Ya construido (rama `feature/mejoras-negocio`, en `develop` por el PR #27; V29, paquete `venta/`)
Revisarlo antes de tocar nada para no duplicar.

**Datos**
- `modulo` `VENTAS` en `plan_modulo` de PROFESIONAL y EMPRESARIAL; constante `Modulos.VENTAS`.
- `tarifa_impuesto`: `id, empresa_id, codigo, nombre, porcentaje, activo` (IVA19, IVA5, EXENTO, EXCLUIDO sembradas por
  empresa; productos existentes quedan con IVA 19 %).
- `producto`: `codigo_barras VARCHAR(50) NULL` (único por `(empresa_id, codigo_barras)`), `tarifa_impuesto_id`,
  `vendible`, `precio_incluye_impuesto`.
- `lista_precio`: `id, empresa_id, nombre, es_general, activo` ("General" sembrada por empresa).
- `precio_producto` (historial, nunca se sobrescribe): `id, empresa_id, lista_precio_id, producto_id, unidad_medida_id,
  precio, precio_minimo, vigente_desde, vigente_hasta, creado_por_usuario_id, motivo`. Cambiar un precio **cierra** la
  vigencia del anterior y crea uno nuevo. `precio_minimo` es el piso: vender por debajo exige autorización de supervisor
  (se aplica en la 004).
- `empresa.precios_delegados_compras` (D8).

**API** (`PreciosController`, `@RequiereModulo(VENTAS)`, roles ADMIN_SISTEMA, ADMIN_COMPRAS si está delegado, AUDITOR
lee)

| Método y ruta | Qué hace |
|---|---|
| `GET/POST /api/ventas/impuestos`, `PATCH /api/ventas/impuestos/{id}` | Tarifas de impuesto (crear, activar o desactivar) |
| `GET/POST /api/ventas/listas`, `PUT /api/ventas/listas/{id}` | Listas de precios |
| `GET/PUT /api/ventas/precios`, `GET /api/ventas/precios/historial` | Precio vigente por lista, fijarlo, historial |
| `PUT /api/ventas/productos/{productoId}` | Datos de venta del producto (tarifa, código de barras, vendible) |

La spec original proponía `/api/precios`, `/api/listas-precio` y `/api/impuestos`; **se adoptan las rutas ya
construidas** bajo `/api/ventas/` y la spec se corrige a ellas.

**Cálculo:** `CalculoMargen` (Java) separa base e impuesto del precio con IVA incluido y calcula el margen contra el
último costo de compra. Auditoría `PRECIO_CONFIGURAR`, `PRECIO_CAMBIAR`, `PRODUCTO_EDITAR`.

**Pantalla:** `/app/precios` (precio por lista, margen, historial).

**Pruebas:** `losPreciosDeVentaSonDeCadaEmpresaYSoloLosFijaElDueno` en `AislamientoEmpresasTest` y pruebas unitarias de
precios y margen.

## Lo que falta (rama `feature/ventas-catalogo`, migración V31)

**Datos (V31)**
- `modulo` `CAJA` (planes PROFESIONAL y EMPRESARIAL) y `CARTERA` (solo EMPRESARIAL); constantes en `Modulos`.
- `rol` `SUPERVISOR_VENTAS` y `VENDEDOR` (catálogo global); constantes en `Roles`.
- Un solo precio vigente por `(empresa_id, lista_precio_id, producto_id, unidad_medida_id)`: columna generada
  `vigente_unico` = 1 cuando `vigente_hasta IS NULL` (NULL en otro caso) y `UNIQUE` sobre esas columnas + `vigente_unico`.
  Hoy el bloqueo pesimista solo protege cuando ya existe un precio vigente; el primer precio de un producto queda
  expuesto a dos inserciones simultáneas.

**Precio por unidad (D9)**
- `PrecioRequest` recibe `unidadMedidaId` opcional (por defecto la unidad del producto); la consulta del vigente y su
  bloqueo filtran también por unidad. La unidad debe tener conversión con la del producto (`UnidadMedidaService`).

**Cambio masivo por Excel**
- `POST /api/ventas/precios/carga` reutiliza la carga masiva existente (`ProductoCargaService`): columnas `codigo`,
  `lista`, `unidad`, `precio`, `precio_minimo`, `motivo`. Todo o nada: si una fila falla no se guarda nada y se informa
  la fila.

**Búsqueda para vender**
- `GET /api/ventas/productos?q=&listaId=&bodegaId=` para CAJERO, VENDEDOR y SUPERVISOR_VENTAS: busca por código, código
  de barras o nombre (solo `vendible`), trae el precio vigente de la lista (General por defecto) y el stock de la bodega.
  En la 004 el POS le pasa la bodega de su caja. Índices por `codigo` y `codigo_barras` para que sea rápida.

**Frontend:** botón de carga por Excel en `/app/precios`, selector de unidad al fijar un precio y asignación de los roles
nuevos en usuarios. Regenerar tipos con `npm run api:tipos`.

## Pruebas
- Unitarias: precio con y sin IVA incluido, margen, conversión de unidad del precio, validación del Excel.
- Integración (MySQL real): dos hilos guardan el primer precio del mismo producto a la vez ⇒ uno gana y el otro recibe un
  conflicto; nunca hay dos vigentes.
- Fuga: la búsqueda para vender y la carga por Excel no ven productos ni listas de otra empresa.
- Permisos: el cajero busca pero no fija precios; el jefe de compras fija precios solo si están delegados.
- Frontend: la pantalla de precios envía la unidad elegida y muestra los errores del Excel por fila.
