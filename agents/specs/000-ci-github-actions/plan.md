# Plan 000

## Flujo principal: `.github/workflows/ci.yml`
Corre en cada PR hacia `develop` y `main`, y en cada push a esas ramas.

| Trabajo | Pasos |
|---|---|
| `backend` | Java 21 (Temurin) con caché de Maven → servicio MySQL 8 con `supply_core_test` → `./mvnw verify` (pruebas, incluida la de fuga entre empresas) → Javadoc con `-Ddoclint=all` → reporte JaCoCo como artefacto |
| `frontend` | Node 24 (la misma versión del `Dockerfile` del frontend) con caché de npm → `npm ci` → `npm test -- --watch=false` → `npm run build` (producción) |
| `docker` | Construye las imágenes `api` y `web` (sin publicarlas) → escaneo con Trivy (falla con vulnerabilidades CRÍTICAS) |
| `e2e` | Se agrega en la spec 010: `docker compose up` con un `.env` de prueba generado en el job → siembra la demo → Playwright |

Notas del backend:
- `application-test.yaml` apunta a `localhost:3306`; la clave sale de `DB_PASSWORD` (en local, de
  `application-local.yaml`, que no existe en el CI). El job genera una clave aleatoria (y un `JWT_SECRET`), las
  enmascara y las pasa como `DB_PASSWORD` y `DB_USER=root`. Como un `services:` de GitHub arranca antes de los pasos y no
  puede usar una clave generada en el job, MySQL 8.4 se levanta con `docker run` en un paso y se espera a que responda.
- `mvnw` no tiene permiso de ejecución en el repositorio: el job lo llama con `sh mvnw`.
- JaCoCo no está en el `pom.xml`: se agrega el plugin `jacoco-maven-plugin` (`prepare-agent` + `report` en `verify`).
  Es dependencia de build nueva: pedir el visto bueno antes de agregarla.

## Otros flujos
- `.github/workflows/dependency-review.yml`: `actions/dependency-review-action` en cada PR, falla con severidad alta.
- `.github/workflows/codeql.yml`: CodeQL semanal (y en PR a `main`) para Java y TypeScript.
- `dependabot.yml` ya existe; no se cambia.

## Seguridad del CI
- Sin secretos reales: las claves de prueba se generan dentro del job. Gemini y Claude no se usan en CI
  (`IA_PROVEEDOR=ninguno`).
- `permissions: contents: read` por defecto; solo CodeQL pide `security-events: write`.
- Acciones fijadas por versión.

## Protección de ramas (la configura el dueño del repositorio)
`develop` y `main` exigen que pasen `backend`, `frontend` y `docker` antes de mezclar, y al menos una revisión.

## Pruebas
- El propio CI es la prueba: un PR de prueba en verde y otro con una prueba rota en rojo.
- Local antes de subir: `./mvnw verify` y `npm test -- --watch=false && npm run build` pasan.
- Revisar el registro del job y comprobar que no imprime la clave generada.
