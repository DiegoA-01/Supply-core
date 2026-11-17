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
