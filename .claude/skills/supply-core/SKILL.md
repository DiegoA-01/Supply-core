---
name: supply-core
description: Contexto del proyecto Supply-Core (abastecimiento, inventario, compras con IA). Usar para cualquier tarea de este repo — programar módulos, migraciones Flyway, JWT, reglas de negocio, casos de uso UC-01..UC-16, frontend Angular, o retomar el trabajo.
---

# Supply-Core — índice

Lee solo la referencia que la tarea necesita. No abras `docs/` (PDF/PNG/SQL históricos): todo lo útil está aquí.

| Si la tarea es… | Lee |
|---|---|
| Retomar / "¿qué sigue?" / cerrar una tarea | `references/estado.md` |
| Orden de módulos y archivos que lleva cada uno | `references/plan-backend.md` (solo el paso actual) |
| Reglas, estados, casos de uso, validaciones | `references/reglas-negocio.md` |
| Tablas, columnas, enums, qué tabla va en qué módulo | `references/modelo-datos.md` |
| Crear código backend (módulo, entity, service, DTO) | `references/backend.md` |
| Login, JWT, roles, permisos | `references/auth-jwt.md` |
| SaaS multiempresa, roles por empresa, módulos, plan de fases | `references/saas.md` |
| Cambiar el esquema | `references/flyway.md` |
| IA, análisis, score, ranking | `references/ia.md` |
| Angular | `references/frontend.md` |

## Forma de trabajar (ahorro de tokens)
1. Antes de explorar, mira `estado.md` y la referencia del tema. No releas lo que ya está en contexto.
2. Usa Grep/Glob dirigidos; lee archivos por rango (`offset/limit`). Para V1, Grep `CREATE TABLE nombre` con `-A 25`.
3. No leas dependencias, `target/`, ni todo `src/`. Inspecciona solo el módulo que tocas y sus vecinos directos.
4. Respuestas cortas: qué cambió, dónde, cómo probarlo. Sin repetir el código escrito.
5. Al terminar una tarea: actualiza `estado.md` (1–2 líneas) y, si se tomó una decisión nueva, agrégala a `CLAUDE.md` › Decisiones vigentes.
6. Si una regla de aquí contradice el código, avisa y corrige la referencia; no sigas a ciegas.
