# Supply-Core

Plataforma web de abastecimiento (inventario, proveedores, solicitudes, RFQ, cotizaciones con IA, orden, pago registrado, recepción) para UNA empresa. Proyecto formativo. Responder en español.

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
7. Nada fuera del alcance (ventas, multiempresa, pasarela de pagos, DIAN, microservicios).

## Decisiones vigentes
- JWT con jjwt 0.13.0 (HS256, emisor `supply-core`) dentro de `JwtUtil`; filtro, roles y login propios, sin Spring Security. Hash de contraseñas con BCrypt (`spring-security-crypto` solo).
- Proveedores se autorregistran (pendientes) y ADMIN_COMPRAS los habilita; luego cotizan desde su portal.
- El proveedor inicia sesión como `usuario` con `proveedor_id` + rol PROVEEDOR (un solo login).
- Esquema solo por Flyway (`ddl-auto: none`). Nunca editar un `V*` ya ejecutado: crear `V{n+1}__...sql`.
- Fuente de verdad del esquema: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`.

## Contexto detallado
Usar la skill `supply-core` (`.claude/skills/supply-core/`). Estado y siguiente tarea: `references/estado.md`.

## No leer (caro o desactualizado)
`docs/` (PDF, PNG, docx, SQL históricos), `target/`, `node_modules/`, `.github/modernize/`, `backend/almacenamiento/` (archivos subidos). Su contenido útil ya está resumido en la skill. Para consultar V1 usar Grep por tabla, no leerlo entero.
