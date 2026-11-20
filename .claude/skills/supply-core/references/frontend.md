# Frontend (Angular)

Estado (2026-11-15): `frontend/` creado en `feature/frontend-initial` (10 vistas del mockup) y ampliado en `feature/frontend-vistas` (sale de la anterior). Angular 22.2 + Angular Material 22.2 (MIT) + Vitest. Comandos y estructura: `frontend/README.md`.

## Vistas fuera del mockup (2026-11-15, sin boceto previo, con el mismo sistema visual)
- `/app/usuarios`: usuarios y roles (ADMIN_SISTEMA edita; AUDITOR solo ve). Roles con su descripción, centro de costo, restablecer contraseña, activar/desactivar. Los usuarios PROVEEDOR no se crean aquí (nacen en el portal).
- `/app/solicitudes/nueva` y `/:id/editar`: el SOLICITANTE arma la solicitud (buscador de productos), la guarda en borrador y la envía. El monto lo calcula el backend **al enviar**; `PRESUPUESTO_EXCEDIDO` y `SOLICITUD_DUPLICADA` piden justificación y se reenvía. La bandeja abre en "Mis solicitudes" para quien solo pide, con editar/enviar/cancelar.
- `/app/perfil`: datos propios y cambio de contraseña (actual + nueva + confirmación, ojo, regla de `core/sesion/clave.ts` igual a `ReglasPassword`).
- `/app/cotizaciones/nueva`: el COMPRADOR crea la RFQ desde una solicitud aprobada (fecha límite, condiciones, especificaciones, proveedores o invitación automática por categoría), queda en borrador y se abre. El análisis permite abrir o cerrar la RFQ.
- Detalle de la orden: tesorería (ADMINISTRATIVO) registra la factura (montos tal como vienen impresos + archivo) cuando ya hubo recepción.
- Consola: `/plataforma/planes` (crear/editar planes y sus módulos), `/plataforma/auditoria` (auditoría global paginada por empresa) y cambio de plan de una empresa en Empresas.

## Lectura con IA y exportaciones (2026-11-17, fase 1 de `PLAN_FRONTEND.md`)
- `features/cotizaciones/extraccion.*` (`<sc-extraccion>`): dentro del análisis, una tarjeta por cotización recibida como archivo (`origen = ARCHIVO`). Muestra el estado y el modelo, las alertas (`TOTAL_NO_CUADRA` con su explicación y el total impreso copiado por la IA), los datos leídos con su confianza (por defecto solo los pendientes), confirmar o corregir cada dato, "Leer de nuevo con IA" y "Digitar a mano". Mientras la IA lee consulta cada 3 segundos. Al cambiar algo avisa al análisis para que se recargue. Ven la lectura COMPRADOR, ADMIN_COMPRAS y AUDITOR; editan los dos primeros. La pantalla nunca calcula montos.
- Exportaciones: solicitudes (Excel y envío al correo, con los filtros de la lista), existencias (envío al correo) y orden de compra (envío al correo del PDF). El correo siempre va al usuario de la sesión.
- Factura: botón "Cruzar de nuevo con lo recibido" (ADMINISTRATIVO, factura CUADRADA o EN_REVISION).
- Tipos regenerados contra el backend actual (`alertas` ya viene en `ProcesamientoResponse`).

## Cuenta y seguridad (2026-11-18, fase 2 de `PLAN_FRONTEND.md`)
- Ingreso de empresa en dos pasos: si el login trae `desafioMfa` no hay sesión todavía; se pide el código de 6 dígitos (y la clave para vincular la app si es la primera vez) y se canjea en `/api/auth/mfa` (ruta pública en el interceptor; `/mfa/configurar` y `/mfa/activar` sí llevan token). Sin librería de QR: se muestra la clave y un enlace `otpauth://` para el celular.
- `/recuperar`: recuperación de clave en dos pasos (código de empresa y correo; luego el código que llega por correo y la contraseña nueva). El backend manda un código, no un enlace. Mismo aviso exista o no la cuenta.
- Perfil: configurar y activar el segundo factor. Usuarios: chip MFA y botón para reiniciarlo (ADMIN_SISTEMA). `UsuarioResponse` ahora trae `mfaActivo`.
- `/app/configuracion/notificaciones` (ADMIN_SISTEMA): interruptores de correo y campana por evento.
- Falta: editar el código de ingreso de una empresa (no existe la ruta en el backend; ver `PLAN_FRONTEND.md`).

## Movimientos de stock (2026-11-18, fase 3 de `PLAN_FRONTEND.md`)
- `/app/inventario/movimientos` (todos los roles internos con el módulo INVENTARIO; menú "Movimientos de stock" y botón en Productos y stock). Dos pestañas: **Existencias por bodega** (`GET /api/inventario/stock`, filtro por bodega en el servidor y por texto en pantalla) y **Kardex** (`GET /api/inventario/movimientos` con producto, bodega, tipo y fechas, 25 por página, exportar a Excel o al correo; el kardex exige elegir producto).
- Registrar movimiento (solo ALMACENISTA, no en mora): salida por consumo, ajuste que suma, ajuste que resta y traslado. La pantalla pide solo las bodegas que aplican a cada tipo y el motivo (obligatorio en ajustes, mínimo 5 caracteres); el backend vuelve a validar y avisa si el producto quedó bajo su mínimo. Las entradas por compra, devoluciones y despachos nunca se registran aquí.
- Buscador de producto: escribe 2 letras y elige de la lista (consulta al backend, 15 resultados), sin cargar todo el catálogo.
- Reorden sugerida y carga masiva ya estaban completas (revisar, luego aplicar todo o nada, errores por fila, plantilla).

## Rotación de productos (2026-11-18, pedido del dueño del negocio)
- Backend: `GET /api/reportes/rotacion?desde&hasta&categoriaId` (`RotacionService`, `RotacionController`). La base suma los movimientos; Java calcula. **Salida** = consumo interno + despachos − despachos anulados (no cuentan ajustes ni traslados). **Compra** = entradas por recepción − devoluciones. Por producto: salidas, comprado, consumo diario, días que alcanza el stock (stock × días ÷ salidas), fecha estimada de agotamiento, última salida y días sin salir, valor del stock y clase: ALTA (alcanza 30 días o menos), MEDIA, BAJA (más de 90), SIN_MOVIMIENTO (hay stock y no salió nada: dinero parado), AGOTADO (se movía y hoy no hay), SIN_ACTIVIDAD. "Por agotarse" = alcanza 15 días o menos. Período por defecto 90 días, máximo 366. Ven el reporte ADMIN_SISTEMA, ADMIN_COMPRAS, COMPRADOR, APROBADOR, ADMINISTRATIVO, AUDITOR y ALMACENISTA (módulo REPORTES). Prueba de fuga entre empresas en `AislamientoEmpresasTest`.
- Frontend: `/app/inventario/rotacion` (menú "Rotación de productos"). Tablero con períodos rápidos (30 días a 12 meses u otras fechas) y categoría; KPIs (por agotarse, agotados, sin movimiento, dinero parado); barra de distribución por tipo de rotación; los que más y menos salen (barras); "se acaban pronto" con fecha estimada; "no rotan" con el dinero parado; tabla completa con búsqueda, filtro por tipo y orden por columna. La pantalla solo ordena, filtra, cuenta y dibuja.
- Pendiente de esta pantalla: filtro por bodega y exportar a Excel (el backend aún no lo ofrece).

## Despachos y pagos (2026-11-18, fase 4 de `PLAN_FRONTEND.md`)
- `/app/despachos` (ven ALMACENISTA, ADMIN_COMPRAS, COMPRADOR y AUDITOR; módulo DESPACHOS): lista con filtros y detalle. El ALMACENISTA arma el borrador (bodega, cliente, factura o ticket y productos con unidad y cantidad), lo corrige, lo confirma (descuenta el stock y avisa qué productos quedaron bajo su mínimo) o lo anula con motivo; ADMIN_COMPRAS también anula. Un despacho confirmado no se edita: se anula (el stock vuelve) y se arma otro. **El rol CAJERO no interviene**: en el backend los despachos son de bodega.
- `/app/pagos` (ven ADMINISTRATIVO, ADMIN_COMPRAS y AUDITOR; módulo ORDENES_PAGOS): pagos de todas las órdenes con filtros (estado, proveedor, fechas de pago y solo los que no tienen comprobante). Tesorería (ADMINISTRATIVO) adjunta el comprobante (una sola vez), lo descarga y anula pagos con motivo (mínimo 5 caracteres); el pago anulado deja de sumar a lo pagado de la orden. La pantalla no suma valores.
- `<sc-pago-acciones>` se usa en `/app/pagos` y en el detalle de la orden. `BuscadorProducto` pasó a `core/ui/buscador-producto.ts` (lo usan movimientos y despachos).
- Estado de cuenta del proveedor: ya está en Reportes; el backend no tiene exportación de él, así que no hay botón de Excel.

## Decisiones y órdenes (2026-11-18, fase 5 de `PLAN_FRONTEND.md`)
- `/app/adjudicaciones`: bandeja de adjudicaciones por aprobar (las de mi rol o de uno delegado, sin las que yo adjudiqué). Muestra RFQ, proveedor, monto, si se apartó de la recomendación de la IA (con su justificación) y qué rol aprueba (`/api/politicas-aprobacion/aplicable`). Aprobar o rechazar; rechazar exige motivo. La ven todos los roles internos con el módulo RFQ_COTIZACIONES (un delegado puede no tener un rol de aprobador).
- `/app/delegaciones`: delegaciones que di y las que recibí; delegar a otra persona por fechas y revocar. Backend nuevo: `GET /api/delegaciones/candidatos` (solo id y nombre de los usuarios internos activos, menos uno mismo; solo quien tiene un rol que aprueba en alguna política activa), porque los aprobadores no pueden listar usuarios. Prueba de fuga entre empresas en `AislamientoEmpresasTest`.
- `/app/ordenes/directa` (COMPRADOR): elige una solicitud aprobada, ve los contratos vigentes que la cubren (cobertura, tope, lo aprobado y totales calculados por el backend) y emite la orden al precio pactado. Enlace "Orden directa" en la lista de órdenes. La ruta va antes de `ordenes/:id`.
- Orden: el COMPRADOR cancela (con motivo) o crea una versión nueva (motivo y cambios de entrega o cantidades; manda solo lo que cambió) mientras no esté despachada. Recepción: ALMACENISTA y ADMIN_COMPRAS cierran la recepción (el motivo es obligatorio si queda faltante). RFQ: el COMPRADOR la cancela en borrador o abierta.

## Configuración (2026-11-18, fase 6 de `PLAN_FRONTEND.md`)
- `/app/configuracion`: índice de todo lo que define cómo trabaja la empresa; cada tarjeta dice si el usuario puede cambiarlo o solo consultarlo, y solo muestra lo de los módulos activos.
- Catálogos simples con **una sola pantalla genérica** (`features/configuracion/catalogo.*`, configurada en `catalogos.ts`): `/app/configuracion/:catalogo` con sedes, centros-costo, bodegas, categorias y unidades-medida (lista con filtro activos/inactivos, crear, editar, activar y desactivar; desactivar no borra). Roles como el backend: sedes, centros y bodegas = ADMIN_SISTEMA; categorías y unidades = ADMIN_COMPRAS o ADMIN_SISTEMA. Una bodega con stock no se desactiva; de una unidad solo se edita el nombre (código, magnitud y factor quedan fijos). Convertidor de unidades en la pantalla de unidades (lo calcula el backend). Un catálogo nuevo = una entrada en `CATALOGOS`.
- `/app/configuracion/politicas` (ADMIN_COMPRAS edita): rangos [desde, hasta) por tipo (solicitud o adjudicación) con el rol que aprueba (APROBADOR o ADMIN_COMPRAS); entre las activas del mismo tipo no se superponen; un monto que ningún rango cubre no necesita aprobación.
- `/app/configuracion/pesos`: pesos de evaluación (deben sumar 100; cada cambio crea una versión y los análisis ya hechos conservan los suyos).
- `/app/configuracion/presupuestos`: por centro de costo y mes; comprometido, disponible y uso los calcula el backend; al editar solo cambia el monto.
- `/app/configuracion/contratos`: contratos con precios pactados (con tope opcional de cantidad), detalle con lo consumido y disponible, cambiar vigencia y anular con motivo; alimentan la orden directa.
- `/app/proveedores` ahora tiene ficha: habilitar, rechazar y suspender con motivo, volver a habilitar a un suspendido, editar contacto y categorías, y ver las calificaciones.
- `/app/inventario`: editar, desactivar y reactivar productos (ADMIN_COMPRAS); el stock no se edita ahí.

## Portal del proveedor (2026-11-18, fase 7 de `PLAN_FRONTEND.md`)
- `/registro/:empresa` (público; el código de la empresa va codificado en la dirección porque puede llevar espacios): el proveedor llena razón social, NIT (solo números, con dígito de verificación opcional), teléfono, dirección, categorías en que cotiza y los datos de quien administrará el portal. Queda PENDIENTE y **no puede ingresar** hasta que ADMIN_COMPRAS lo habilite. En el ingreso hay un enlace "regístrate aquí" que usa el código escrito.
- `/portal/perfil`: su ficha (estado de la habilitación, score, categorías), editar contacto y categorías, y las calificaciones que recibió. La razón social y el NIT los cambia la empresa. Backend nuevo: `GET /api/portal/proveedor/categorias` (el proveedor no puede leer `/api/categorias`, que es solo para usuarios internos); con prueba de fuga entre empresas.
- `/portal/ordenes`: además de aceptar, rechazar, preparar y despachar, ahora muestra lo que el cliente ya recibió (pedido, aceptado, rechazado, faltante) y los rechazos con su motivo.

## Auditoría de la empresa (2026-11-18, fase 8 de `PLAN_FRONTEND.md`)
- `/app/auditoria` (AUDITOR y ADMIN_SISTEMA; módulo AUDITORIA): quién hizo qué, cuándo y desde qué IP, incluidos los intentos fallidos; solo lectura. Filtros por usuario, acción, entidad, número del registro, resultado y fechas (más Hoy, 7 y 30 días), 25 por página, y detalle desplegable con el código de la acción, la IP y el contexto; enlace a la orden o la RFQ cuando el registro es de una de ellas.
- `features/auditoria/textos.ts` traduce las 96 acciones y las 28 entidades del backend (`AccionAuditoria` y `EntidadAuditada`); un código nuevo se muestra tal cual hasta que se agregue su texto. Hay que sumar el texto cuando el backend agregue una acción.
- Se eliminó el marcador `features/pendiente` (ya no lo usaba ninguna ruta).

## Reabastecimiento y tablero del dueño (2026-11-18)
- `/app/inventario/reabastecimiento`: estados del plan como tarjetas que filtran, tabla con consumo, días que alcanza, plazo (de la empresa o del producto, y el real como pista), fecha límite para pedir y cantidad; detalle con la cuenta. ADMIN_COMPRAS ajusta el plazo del producto; el SOLICITANTE elige productos y crea la solicitud en borrador (abre su edición).
- `/app/tablero` (ADMIN_SISTEMA, ADMIN_COMPRAS, AUDITOR): KPIs, compras por mes (barras), gasto por categoría y proveedor, distribución de rotación, más y menos movidos, qué pedir. Sin librería de gráficos: barras con CSS; todo número viene del backend.
- Datos de la empresa suma los días de entrega, seguridad y cobertura por defecto.
- Selector de sede (`core/ui/selector-sede.ts`) en tablero, plan de reabastecimiento y rotación; aparece solo con dos o más sedes. El tablero de toda la empresa compara las compras por sede.

## Decisiones (2026-11-05)
- **Una sola app SaaS con tres zonas**: `/app` (empresa, `empresaGuard`), `/portal` (rol PROVEEDOR, `proveedorGuard`), `/plataforma` (dueño con MFA, `plataformaGuard`). Cada pantalla se carga perezosa.
- **Librería**: Angular Material solo para comportamiento (menús, avisos, tooltips); el aspecto sale de tokens CSS en `styles.scss` tomados del mockup (primario `#d93a0b`). PrimeNG descartado: v22 exige licencia comercial con clave.
- **Sesión**: JWT en memoria + refresh en `sessionStorage`, todo dentro de `SesionService` (pasar a cookie `HttpOnly` en SaaS 3 toca solo ese archivo y el backend). `authInterceptor` renueva una vez ante 401 (un solo canje compartido: el refresh sirve una vez) y si no puede manda a `/ingreso?motivo=sesion`. El token del dueño solo va a `/api/plataforma/**`.
- **Tipos**: `npm run api:tipos` genera `core/api/esquema.ts` con openapi-typescript 7.13 (con `overrides` en package.json para usar TypeScript 6). Alias en `core/api/modelos.ts`.
- **Desarrollo sin CORS**: `ng serve` pasa `/api` a `localhost:8080` con `proxy.conf.json`; `environment.apiUrl` vacío.
- **Formato**: es-CO solo como formato (`PesosPipe`, `NumeroPipe`, `FechaPipe` "05 nov 2026"). Estados legibles con `EtiquetaPipe` / `<sc-estado>`.
- **Salir siempre a la vista**: botón "Cerrar sesión" fijo al pie del menú lateral de `/app` (además del menú del usuario, que ahora muestra una flecha). El portal y la consola ya tenían "Salir" arriba a la derecha.
- **Contraseñas**: todo campo de contraseña lleva el botón de ojo (`.con-ojo` en `styles.scss`, señal `verClave`) para mostrarla u ocultarla; arranca oculta. Hoy en `/ingreso`, `/plataforma/ingreso` y la contraseña inicial del administrador en la consola.
- **Adaptable**: todas las vistas; menú lateral como panel bajo 1024 px; recepción y cotización del portal pasan a tarjetas en el celular.

## Lineamientos
- Componentes standalone, señales, formularios reactivos, control de flujo nuevo (`@if`, `@for`).
- Estructura: `core/` (api, sesion, http, servicios, formato, navegacion, ui), `layout/`, `features/<pantalla>/`.
- Las llamadas HTTP solo en `core/servicios` (uno por área).
- Menú y guards con los mismos roles y módulos del backend (`core/navegacion/menu.ts`); módulo apagado = opción bloqueada con candado; suscripción morosa = aviso y botones de acción ocultos.
- La UI nunca calcula dinero ni stock: totales, saldo, diferencia de factura, reparto de la recepción y reorden vienen del backend. Lo único que se calcula es dibujo (alto de barras) y conteos.
- Portal del proveedor: rutas separadas para rol PROVEEDOR.
- URL del backend en `environments`; ninguna clave (IA, Maps) en el frontend.

## Pendiente
- Plan completo y por fases: `PLAN_FRONTEND.md` (fases 0 y 1 hechas; el kardex se exporta en la fase 3, donde se elige el producto).
- Pantallas de las fases 2 a 8 hechas (ver `PLAN_FRONTEND.md`). Datos y reglas de la empresa (`/app/configuracion/empresa`, `PUT /api/empresa`) agregada el 2026-11-18.
- Datos que la API no trae y el mockup muestra: nombre del solicitante en la bandeja (solo ADMIN_SISTEMA/AUDITOR leen `/api/usuarios`), usuarios por empresa en la consola, variación frente al mes anterior.
- Exportar a Excel los reportes (backend, parte 2). Recuperación de clave (SaaS 3).

## Mejoras de negocio (2026-11-19, rama `feature/mejoras-negocio`)
- `/app/pedidos` (todo usuario interno): pedir un producto del catálogo o con descripción libre; compras (ADMIN_COMPRAS, COMPRADOR) ve todos y responde; descartar exige explicación. `?productoId=` lo llena desde el plan ("Necesito este producto").
- `/app/precios` (módulo VENTAS): precio de venta con IVA incluido, impuesto, historial y margen por producto y global; todo lo calcula el backend. Delegar precios a compras: casilla en Datos de la empresa.
- `/app/catalogos` (módulo PROVEEDORES; ADMIN_COMPRAS, COMPRADOR, AUDITOR, ADMIN_SISTEMA): pestañas Oportunidades y Catálogo completo; confirmar, cambiar o descartar la equivalencia (solo ADMIN_COMPRAS y COMPRADOR). Servicio `catalogos.proveedor.service.ts`.
- `/portal/catalogo`: el proveedor sube su Excel (todo o nada, errores por fila) y ve su lista; nunca ve stock ni necesidades.
- Plan de reabastecimiento: comparación de sedes, chip de traslado, mínimo y máximo por sede (ADMIN_COMPRAS) y "Ofertas en catálogos de proveedores" en el detalle.
- Reportes, Inicio e Informe de compras con selector de sede o tabla "Compras por sede".

## Ventas y caja (2026-11-20, specs 004 y 005)
- `/app/pos` (CAJERO, SUPERVISOR_VENTAS cobran; VENDEDOR arma pedidos): sin turno lleva a `/app/caja/mi-turno`. Nunca calcula dinero: cada cambio llama a `POST /api/ventas/cotizar` (pausa de 250 ms). `?pedido=id` carga un pedido de venta.
- `/app/caja/mi-turno`: el cierre es ciego, el esperado solo se ve después de guardar. `/app/caja` sigue siendo la vista del supervisor.
- `/app/ventas` (supervisor, dueño, auditor, vendedor): historial, detalle y reimpresión; límites de descuento (los cambia el dueño). El cajero no tiene historial (cierre ciego).
- Tiquete: `htmlTiquete` + `imprimirTiquete` en `features/pos/tiquete.ts`. Servicio: `core/servicios/venta.service.ts` (distinto de `ventas.service.ts`, que es el catálogo de precios).
- `/app/cartera` (cajero, supervisor, dueño, auditor; módulo CARTERA): edades, clientes en mora o bloqueados, cuentas y abonos con su recibo; el supervisor o el dueño habilita a un cliente en mora hasta una fecha. El cupo se fija en Clientes.
- Mi turno recibe abonos (con el módulo CARTERA) e imprime el recibo (`htmlRecibo` en `features/pos/tiquete.ts`). En el POS, Crédito y Saldo a favor aparecen solo con un cliente registrado; la tarjeta del cliente muestra crédito disponible, saldo a favor y si está bloqueado.
