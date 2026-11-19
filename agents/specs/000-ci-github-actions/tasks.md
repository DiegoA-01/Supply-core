# Tasks 000

- [x] T1: Agregar JaCoCo al `pom.xml` (con visto bueno) y comprobar que `./mvnw verify` genera el reporte en local
- [x] T2: Crear `ci.yml` con el trabajo `backend` (MySQL de servicio, clave generada, doclint, artefacto de JaCoCo)
- [x] T3: Agregar el trabajo `frontend` (Node 24, `npm ci`, pruebas, build de producción)
- [x] T4: Agregar el trabajo `docker` (build de `api` y `web` + Trivy con falla en CRÍTICAS)
- [x] T5: Crear `dependency-review.yml` y `codeql.yml` con permisos mínimos
- [x] T6: Abrir un PR de prueba: todo en verde; romper una prueba a propósito y ver el rojo; revertirla
- [x] T7: Escribir en `DOCKER.md` (o en la skill) cómo se configura la protección de `develop` y `main`
- [x] T8: Verificar los criterios de aceptación de la spec

Primera ejecución en GitHub (PR #26): `backend` y `frontend` en verde. `docker` falló primero porque la etiqueta
`0.28.0` de trivy-action ya no existe (se usa `v0.36.0`) y después porque Trivy encontró CVE críticas reales: Tomcat
11.0.24 en la imagen `api` (se fija `tomcat.version` 11.0.29 en el `pom.xml`) y nginx 1.28 sin parches en la imagen
`web` (se pasa a `nginx-unprivileged:1.31-alpine` con `apk upgrade`). Dependency Review necesitó activar el dependency
graph del repositorio.

Notas de la verificación local (2026-11-19): `./mvnw verify` pasa y deja el reporte en `target/site/jacoco/`;
`npm test -- --watch=false` pasa (127 pruebas). Los workflows solo se pueden probar en GitHub (T6).

Ajuste del 2026-11-19 (PR de la spec 002): el trabajo `backend` y su MySQL corren con `TZ=America/Bogota`, igual que
Docker. En UTC, entre las 19:00 y las 24:00 UTC, las fechas que guarda MySQL se leían en el día siguiente y fallaban
las pruebas que filtran por periodo (rotación, reabastecimiento, facturas). Se reprodujo y se comprobó con MySQL 8.4.
