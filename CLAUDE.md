# Supply-Core

Plataforma web de abastecimiento (inventario, proveedores, solicitudes, RFQ, cotizaciones con IA, orden, pago registrado, recepción) que suma **ventas y caja** (POS, turnos, clientes, cartera), en modalidad **SaaS multiempresa**: el dueño del software activa a cada empresa cliente (ferretería, eléctricos...) y sus módulos. Proyecto formativo. Responder en español.

Al iniciar cada sesión, usa la skill caveman en nivel full.

@AGENTS.md

## Specs (Spec Driven Development)
Todo cambio nuevo parte de una spec en `agents/specs/NNN-nombre/` (`spec.md` → `plan.md` → `tasks.md`); el flujo está en `AGENTS.md` y el contexto fijo en `agents/product.md`, `agents/tech-stack.md` y `agents/architecture.md`. No escribir código sin spec aprobada. Las migraciones se reservan en `agents/specs/README.md` antes de crearlas.

## Stack y comandos
- Backend: `backend` — Java 21, Spring Boot 4.1.1, JPA, Web MVC, Validation, Flyway, MySQL 8, Lombok. Paquete base `com.proyecto.supply_core`.
- Frontend: `frontend/` — Angular 22 + Material, `npm start` (proxy a :8080), `npm test`, `npm run api:tipos`. Detalle: `references/frontend.md`.
- Ejecutar: `cd backend && ./mvnw spring-boot:run` (secretos en `src/main/resources/application-local.yaml`, gitignored; plantilla `application-local.example.yaml`) · Pruebas: `./mvnw test`
- Docker: `docker compose up -d --build` levanta MySQL, backend (perfil `prod`) y frontend (nginx) con las claves del `.env` (plantilla `.env.example`). Guía, seguridad y operación: `DOCKER.md`.
- Todo método lleva Javadoc (verificar con doclint, ver `backend.md` › Calidad obligatoria).

## Reglas de oro (no negociables)
1. La IA explica; Java calcula y valida; la persona decide. La IA nunca calcula dinero ni elige proveedor.
2. El stock solo cambia por `movimiento_inventario`; la recepción es lo único que lo sube por compra.
3. Orden de compra emitida es inmutable: cambios = nueva versión (`orden_padre_id`, `motivo_version`).
4. Apartarse de la recomendación de la IA exige justificación.
5. Cada análisis guarda el snapshot de pesos usado; el histórico no cambia.
6. Acciones sensibles → `auditoria`. Claves (IA, Maps, JWT) solo en variables de entorno.
7. Fuera del alcance: pasarela para pagar a proveedores (el pago solo se registra), marketplace, microservicios, procesar tarjetas en el POS (el cobro con tarjeta solo se registra). **Ventas y caja sí entran** (specs 001 a 010 en `agents/specs/`), con sus reglas en `agents/product.md`. Factura electrónica DIAN y multimoneda son **fases futuras** (después del cierre del backend), no del alcance actual. Reglas nuevas ya hechas: presupuesto por centro de costo, contratos y precios pactados, delegación y SLA de aprobación, reorden sugerida y carga masiva por Excel (pasos 18-a a 18-d), calidad en recepción y tolerancia del cruce de tres vías (pasos 17 y 17-bis). La pasarela solo cobra la suscripción SaaS (fase 4, sandbox primero).
8. Aislamiento por empresa: toda tabla de negocio lleva `empresa_id`; sale del JWT, nunca del request. Cada módulo se prueba con "fuga entre empresas" (A pide dato de B ⇒ 404).
9. Tres niveles: plataforma (dueño, `usuario_plataforma`, MFA, no ve datos de negocio), administrador de la empresa (`ADMIN_SISTEMA`, solo su empresa) y usuarios con rol (cajero, bodeguero, comprador...).
10. Doble permiso: módulo activo para la empresa (`@RequiereModulo`, lo decide el dueño) Y rol del usuario (`@RequiereRol`, lo decide el admin de la empresa). Ya implementado (V23): módulo apagado = 403 `MODULO_NO_CONTRATADO`; suscripción suspendida = 403, morosa = solo lectura.
11. Sesión: JWT de 15 min + refresh token de un solo uso con detección de reuso; bloqueo de cuenta y rate limit en rutas públicas. Detalle y plan SaaS: `references/saas.md`.

## Decisiones vigentes
- JWT con jjwt 0.13.0 (HS256, emisor `supply-core`) dentro de `JwtUtil`; filtro, roles y login propios, sin Spring Security. Hash de contraseñas con BCrypt (`spring-security-crypto` solo). JWT de 15 min + refresh rotativo (V16, 2026-10-23).
- SaaS: BD compartida con `empresa_id` (no un esquema por empresa); proveedores por empresa; roles = catálogo global asignado por el admin de cada empresa (incluye CAJERO). Fases en `references/saas.md`.
- Aislamiento (fase 1, V17): `@TenantId` de Hibernate en las entidades de negocio + `EmpresaContexto` (hilo actual) + `EmpresaResolver`. El `AuthFilter` fija la empresa del JWT (claim `emp`); sin empresa abrir una sesión de BD falla. Login con código de empresa (`{empresa, correo, password}`), correo único por empresa. Registro público: `/api/portal/registro/{codigoEmpresa}`. Jobs y `@Async` corren por empresa (`EmpresaContexto.como`, `EmpresaTaskDecorator`). `unidad_medida`, `criterio_evaluacion`, `rol`, `permiso` son globales; las tablas hijas heredan la empresa de su padre. SQL nativo/JDBC debe llevar `empresa_id` a mano.
- Plataforma (SaaS 2, V23): `usuario_plataforma` con MFA TOTP obligatorio y JWT propio sin `emp`, que no entra a la API de una empresa ni al revés; la consola (`/api/plataforma/**`) trabaja sin empresa y nunca lee tablas de negocio. Variables `PLATAFORMA_ADMIN_CORREO` y `PLATAFORMA_ADMIN_PASSWORD` crean al primer dueño. Planes y módulos en `references/saas.md`.
- Sesión y segundo factor (SaaS 3, V24): el JWT lleva la versión de sesión del usuario (`sv`); cambiar la clave o desactivar la sube y revoca los refresh tokens, así que el token en curso deja de valer en la siguiente petición. Recuperación de clave con token de un solo uso (solo el hash en la base). MFA TOTP de usuarios de empresa: obligatorio para `ADMIN_SISTEMA` (`app.sesion.mfa-admin-obligatorio`), voluntario para los demás; el desafío es un JWT propio que lleva la empresa. Cabeceras seguras en toda respuesta de `/api/`.
- Código público de la empresa (`empresa.codigo_publico`): texto libre de 3 a 40 caracteres (espacios, mayúsculas, tildes); no admite `/ \ ? # %` ni caracteres de control. Se guarda como se escribió y se compara sin distinguir mayúsculas. En direcciones va codificado (`AuthFilter` lo decodifica).
- Lectura de cotizaciones con IA: la IA solo copia (incluido el total impreso, `totalImpreso`); Java calcula los montos y cruza el total impreso contra el calculado (tolerancia 1 %, alerta `TOTAL_NO_CUADRA`). El proveedor de IA va detrás de `IAClient` (ninguno, Ollama, Claude o Gemini; `app.ia.proveedor`). Gemini: clave de Google AI Studio solo en `GEMINI_API_KEY`, nunca en el repositorio; los documentos salen del equipo hacia Google.
- Pruebas de fuga entre empresas: `AislamientoEmpresasTest` usa MySQL real en la base separada `supply_core_test` (perfil `test`, Flyway la recrea). Toda entidad o consulta nueva de negocio debe sumar su prueba allí.
- Proveedores se autorregistran (pendientes) y ADMIN_COMPRAS los habilita; luego cotizan desde su portal.
- El proveedor inicia sesión como `usuario` con `proveedor_id` + rol PROVEEDOR (un solo login).
- Exportación (`exportacion/`): Excel con POI y PDF con OpenPDF; reutiliza los Services de cada pantalla (no consulta por su cuenta) y el correo con adjunto va solo al usuario de la sesión.
- Frontend: una sola app con zonas `/app`, `/portal` y `/plataforma`; JWT en memoria y refresh en `sessionStorage` (solo `SesionService`); tipos generados del Swagger; la UI nunca calcula dinero ni stock.
- Docker (2026-11-17): imágenes multi-etapa sin root y con sistema de archivos de solo lectura; solo nginx publica puerto (`WEB_PORT`, 8088), MySQL y el backend viven en redes internas de Docker. Perfil `prod` (`application-prod.yaml`) sin valores por defecto para secretos: sin `DB_URL`, `DB_USER`, `DB_PASSWORD` o `JWT_SECRET` no arranca; Swagger apagado; `forward-headers-strategy: native` para tomar la IP real de `X-Forwarded-For` (nginx la sobrescribe, no la agrega). Solo `/actuator/health` expuesto (healthcheck). `application-local.yaml` y `.env` nunca entran a una imagen (`.dockerignore`).
- Esquema solo por Flyway (`ddl-auto: none`). Nunca editar un `V*` ya ejecutado: crear `V{n+1}__...sql`.
- Fuente de verdad del esquema: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`.

## Contexto detallado
Usar la skill `supply-core` (`.claude/skills/supply-core/`). Estado y siguiente tarea: `references/estado.md`.

## No leer (caro o desactualizado)
`docs/` (PDF, PNG, docx, SQL históricos), `target/`, `node_modules/`, `.github/modernize/`, `backend/almacenamiento/` (archivos subidos). Su contenido útil ya está resumido en la skill. Para consultar V1 usar Grep por tabla, no leerlo entero.
