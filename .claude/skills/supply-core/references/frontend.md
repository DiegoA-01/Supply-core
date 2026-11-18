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
- Pantallas: auditoría de la empresa (placeholder), despachos de caja (CAJERO), movimientos de inventario (ajustes y traslados), catálogos de configuración (sedes, centros de costo, bodegas, categorías, políticas, pesos, presupuestos, contratos, delegaciones), perfil del proveedor en el portal y su registro público.
- Datos que la API no trae y el mockup muestra: nombre del solicitante en la bandeja (solo ADMIN_SISTEMA/AUDITOR leen `/api/usuarios`), usuarios por empresa en la consola, variación frente al mes anterior.
- Exportar a Excel los reportes (backend, parte 2). Recuperación de clave (SaaS 3).
