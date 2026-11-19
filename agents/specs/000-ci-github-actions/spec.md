# 000 - Integración continua

**Estado:** Hecha (2026-11-19, PR #26) · **Rama:** `feature/ci-github-actions` · **Depende de:** —

## Objetivo
Que cada cambio que llega a `develop` o `main` se compruebe solo: que compila, que pasan todas las pruebas (incluidas
las de fuga entre empresas) y que las imágenes no traen vulnerabilidades críticas. Se construye **primero**, para que
todo lo demás nazca probado.

## Historias
- Como desarrollador, quiero que mi PR muestre si el backend, el frontend y las imágenes de Docker pasan, sin correr nada
  a mano.
- Como dueño del repositorio, quiero que no se pueda mezclar a `develop` ni a `main` si algo falla.
- Como equipo, queremos saber si una dependencia nueva trae una vulnerabilidad conocida antes de mezclarla.

## Criterios de aceptación
- [x] Un PR de prueba hacia `develop` muestra los trabajos `backend`, `frontend` y `docker` en verde.
- [x] Una prueba que falla a propósito pone el PR en rojo.
- [x] El trabajo `backend` corre también las pruebas de fuga entre empresas contra un MySQL real.
- [x] El reporte de cobertura queda descargable desde la ejecución.
- [x] Una dependencia con vulnerabilidad conocida agregada en un PR queda marcada.
- [x] El análisis de código corre cada semana.
- [x] Ninguna clave real aparece en el repositorio ni en los registros del CI.

> Verificado en el PR #26: los tres trabajos en verde; el CI se puso en rojo ante una acción inexistente y ante
> CVE críticas reales (que se corrigieron); el reporte de JaCoCo quedó como artefacto `cobertura-backend`; Dependency
> Review pasa con el dependency graph activado; las claves de prueba se generan y se enmascaran en el job. CodeQL corre
> los lunes: su primera ejecución semanal queda por confirmar.

## Fuera de alcance
- Publicar imágenes o desplegar a un servidor.
- Las pruebas de punta a punta (se activan en la spec 010).
- Configurar la protección de ramas: la hace el dueño del repositorio en GitHub con la guía del plan.
