# Pruebas de punta a punta y de carga (spec 010)

## Punta a punta (Playwright)

Recorrido del mostrador en un navegador real: abrir turno → vender con el lector y efectivo → anular con el PIN del
supervisor → cerrar el turno a ciegas y comprobar que cuadra.

Con el stack de Docker arriba y sembrado:

```bash
py scripts/env_de_prueba.py          # solo la primera vez: .env con claves aleatorias (no se sube a git)
docker compose up -d --build
py scripts/sembrar_demo.py           # deja las cuentas en credenciales-docker.local.json (no se sube a git)
cd e2e
npm ci
npx playwright install chromium
npx playwright test
```

Variables opcionales:
- `E2E_BASE_URL`: dirección de la aplicación (por defecto `http://localhost:8088`, la del stack).
- `E2E_CREDENCIALES`: archivo JSON con las cuentas (por defecto `../credenciales-docker.local.json`). Puede traer
  `cajero` y `supervisor` para usar otras cuentas, por ejemplo las de la base demo local.

En la integración continua corre en el trabajo `e2e` de `.github/workflows/ci.yml` en cada PR.

## Carga (Python, sin dependencias)

```bash
py scripts/prueba_carga.py --ritmo 50 --minutos 5 --cajas 3
```

Crea sus propios cajeros y cajas, vende a la vez en todas y al final comprueba que no hubo errores, que el stock no
quedó negativo y bajó exactamente lo vendido, que los consecutivos de cada caja no tienen huecos y que cada caja cierra
cuadrada. El reporte queda en `reporte-carga.local.json`. En GitHub se lanza a mano con el workflow **Carga**
(pestaña Actions → Run workflow); no corre en cada PR.

Los cajeros, cajas, turnos y ventas que crea la prueba quedan en la base: en GitHub se borran solos (el workflow
termina con `docker compose down -v`), pero en tu equipo se acumulan con cada corrida. Para dejar la demo limpia:
`docker compose down -v`, levantar de nuevo y volver a sembrar (las claves cambian).
