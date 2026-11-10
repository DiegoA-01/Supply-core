# Frontend (Angular)

Estado (2026-11-05): `frontend/` creado en la rama `feature/frontend-initial`. Angular 22.2 + Angular Material 22.2 (MIT) + Vitest. Las 10 vistas del mockup conectadas a la API real. Comandos y estructura: `frontend/README.md`.

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
- Pantallas fuera del mockup: crear/editar solicitud (SOLICITANTE), usuarios y roles, auditoría (hoy placeholder), RFQ nueva, registrar factura, perfil y cambio de clave, consola: planes y auditoría global.
- Datos que la API no trae y el mockup muestra: nombre del solicitante en la bandeja (solo ADMIN_SISTEMA/AUDITOR leen `/api/usuarios`), usuarios por empresa en la consola, variación frente al mes anterior.
- Exportar a Excel los reportes (backend, parte 2). Recuperación de clave (SaaS 3).
