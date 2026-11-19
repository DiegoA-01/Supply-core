# Stack

## Backend (`backend/`)
- Java 21, Spring Boot 4.1.1 (Web MVC, JPA, Validation), Lombok. Paquete base `com.proyecto.supply_core`.
- MySQL 8 (8.4 en Docker). Esquema solo por Flyway (`ddl-auto: none`).
- Seguridad propia sin Spring Security: JWT con jjwt 0.13.0 (HS256), BCrypt (`spring-security-crypto`), MFA TOTP.
- Excel con Apache POI 5.4.1, PDF con OpenPDF 2.0.3 y PDFBox 3.0.5.
- IA de lectura de cotizaciones detrás de `IAClient` (ninguno, Ollama, Claude o Gemini).
- Pruebas: JUnit 5 + Mockito; integración y fuga entre empresas contra MySQL real (`supply_core_test`, perfil `test`).
- Javadoc obligatorio en todo método (`-Ddoclint=all` sin advertencias).

## Frontend (`frontend/`)
- Angular 22 (standalone y signals) + Angular Material 22, TypeScript 6.
- Tipos generados del Swagger (`npm run api:tipos`).
- Pruebas: Vitest 4 (`npm test`).

## Operación
- Docker Compose: `db` (MySQL 8.4), `api` (Temurin 21, perfil `prod`), `web` (nginx sin privilegios, Node 24 para el build).
- CI: GitHub Actions (spec `000-ci-github-actions`).
