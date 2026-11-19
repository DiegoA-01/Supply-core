# Tasks 000

- [ ] T1: Agregar JaCoCo al `pom.xml` (con visto bueno) y comprobar que `./mvnw verify` genera el reporte en local
- [ ] T2: Crear `ci.yml` con el trabajo `backend` (MySQL de servicio, clave generada, doclint, artefacto de JaCoCo)
- [ ] T3: Agregar el trabajo `frontend` (Node 24, `npm ci`, pruebas, build de producción)
- [ ] T4: Agregar el trabajo `docker` (build de `api` y `web` + Trivy con falla en CRÍTICAS)
- [ ] T5: Crear `dependency-review.yml` y `codeql.yml` con permisos mínimos
- [ ] T6: Abrir un PR de prueba: todo en verde; romper una prueba a propósito y ver el rojo; revertirla
- [ ] T7: Escribir en `DOCKER.md` (o en la skill) cómo se configura la protección de `develop` y `main`
- [ ] T8: Verificar los criterios de aceptación de la spec
