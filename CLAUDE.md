# Supply-Core

Plataforma web de abastecimiento (inventario, proveedores, solicitudes, RFQ, cotizaciones con IA, orden, pago registrado, recepción) en modalidad **SaaS multiempresa**: el dueño del software activa a cada empresa cliente (ferretería, eléctricos...) y sus módulos. Proyecto formativo. Responder en español.

Al iniciar cada sesión, usa la skill caveman en nivel full.

## Stack y comandos
- Backend: `backend` — Java 21, Spring Boot 4.1.1, JPA, Web MVC, Validation, Flyway, MySQL 8, Lombok. Paquete base `com.proyecto.supply_core`.
- Frontend: `frontend/` (Angular, aún vacío).
- Ejecutar: `cd backend && ./mvnw spring-boot:run` (secretos en `src/main/resources/application-local.yaml`, gitignored; plantilla `application-local.example.yaml`) · Pruebas: `./mvnw test`
- Todo método lleva Javadoc (verificar con doclint, ver `backend.md` › Calidad obligatoria).

## Reglas de oro (no negociables)
1. La IA explica; Java calcula y valida; la persona decide. La IA nunca calcula dinero ni elige proveedor.
2. El stock solo cambia por `movimiento_inventario`; la recepción es lo único que lo sube por compra.
3. Orden de compra emitida es inmutable: cambios = nueva versión (`orden_padre_id`, `motivo_version`).
4. Apartarse de la recomendación de la IA exige justificación.
5. Cada análisis guarda el snapshot de pesos usado; el histórico no cambia.
6. Acciones sensibles → `auditoria`. Claves (IA, Maps, JWT) solo en variables de entorno.
7. Fuera del alcance: pasarela para pagar a proveedores (el pago solo se registra), marketplace, microservicios. Factura electrónica DIAN y multimoneda son **fases futuras** (después del cierre del backend), no del alcance actual. Reglas nuevas por construir: presupuesto por centro de costo, contratos y precios pactados, delegación y SLA de aprobación, reorden sugerida; calidad en recepción y tolerancia del cruce de tres vías ya están hechos (pasos 17 y 17-bis). La pasarela solo cobra la suscripción SaaS (fase 4, sandbox primero).
8. Aislamiento por empresa: toda tabla de negocio lleva `empresa_id`; sale del JWT, nunca del request. Cada módulo se prueba con "fuga entre empresas" (A pide dato de B ⇒ 404).
9. Tres niveles: plataforma (dueño, `usuario_plataforma`, MFA, no ve datos de negocio), administrador de la empresa (`ADMIN_SISTEMA`, solo su empresa) y usuarios con rol (cajero, bodeguero, comprador...).
10. Doble permiso: módulo activo para la empresa (`@RequiereModulo`, lo decide el dueño) Y rol del usuario (`@RequiereRol`, lo decide el admin de la empresa).
11. Sesión: JWT de 15 min + refresh token de un solo uso con detección de reuso; bloqueo de cuenta y rate limit en rutas públicas. Detalle y plan SaaS: `references/saas.md`.

## Decisiones vigentes
- JWT con jjwt 0.13.0 (HS256, emisor `supply-core`) dentro de `JwtUtil`; filtro, roles y login propios, sin Spring Security. Hash de contraseñas con BCrypt (`spring-security-crypto` solo). JWT de 15 min + refresh rotativo (V16, 2026-10-23).
- SaaS: BD compartida con `empresa_id` (no un esquema por empresa); proveedores por empresa; roles = catálogo global asignado por el admin de cada empresa (incluye CAJERO). Fases en `references/saas.md`.
- Aislamiento (fase 1, V17): `@TenantId` de Hibernate en las entidades de negocio + `EmpresaContexto` (hilo actual) + `EmpresaResolver`. El `AuthFilter` fija la empresa del JWT (claim `emp`); sin empresa abrir una sesión de BD falla. Login con código de empresa (`{empresa, correo, password}`), correo único por empresa. Registro público: `/api/portal/registro/{codigoEmpresa}`. Jobs y `@Async` corren por empresa (`EmpresaContexto.como`, `EmpresaTaskDecorator`). `unidad_medida`, `criterio_evaluacion`, `rol`, `permiso` son globales; las tablas hijas heredan la empresa de su padre. SQL nativo/JDBC debe llevar `empresa_id` a mano.
- Pruebas de fuga entre empresas: `AislamientoEmpresasTest` usa MySQL real en la base separada `supply_core_test` (perfil `test`, Flyway la recrea). Toda entidad o consulta nueva de negocio debe sumar su prueba allí.
- Proveedores se autorregistran (pendientes) y ADMIN_COMPRAS los habilita; luego cotizan desde su portal.
- El proveedor inicia sesión como `usuario` con `proveedor_id` + rol PROVEEDOR (un solo login).
- Esquema solo por Flyway (`ddl-auto: none`). Nunca editar un `V*` ya ejecutado: crear `V{n+1}__...sql`.
- Fuente de verdad del esquema: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`.

## Contexto detallado
Usar la skill `supply-core` (`.claude/skills/supply-core/`). Estado y siguiente tarea: `references/estado.md`.

## No leer (caro o desactualizado)
`docs/` (PDF, PNG, docx, SQL históricos), `target/`, `node_modules/`, `.github/modernize/`, `backend/almacenamiento/` (archivos subidos). Su contenido útil ya está resumido en la skill. Para consultar V1 usar Grep por tabla, no leerlo entero.
