# 000 - Integración continua

**Estado:** En construcción (aprobada el 2026-11-19) · **Rama:** `feature/ci-github-actions` · **Depende de:** —

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
- [ ] Un PR de prueba hacia `develop` muestra los trabajos `backend`, `frontend` y `docker` en verde.
- [ ] Una prueba que falla a propósito pone el PR en rojo.
- [ ] El trabajo `backend` corre también las pruebas de fuga entre empresas contra un MySQL real.
- [ ] El reporte de cobertura queda descargable desde la ejecución.
- [ ] Una dependencia con vulnerabilidad conocida agregada en un PR queda marcada.
- [ ] El análisis de código corre cada semana.
- [ ] Ninguna clave real aparece en el repositorio ni en los registros del CI.

## Fuera de alcance
- Publicar imágenes o desplegar a un servidor.
- Las pruebas de punta a punta (se activan en la spec 010).
- Configurar la protección de ramas: la hace el dueño del repositorio en GitHub con la guía del plan.
