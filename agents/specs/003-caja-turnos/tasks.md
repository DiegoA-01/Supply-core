# Tasks 003

- [x] T1: Reservar la migración; crear `caja`, `turno_caja` (únicos parciales), `movimiento_caja` y la tolerancia por empresa
- [x] T2: CRUD de cajas (bodega de la misma sede) + pruebas + prueba de fuga
- [x] T3: PIN de supervisor (fijar, validar, bloqueo, rate limit) + pruebas
- [x] T4: Autorizaciones de un solo uso (crear, consumir, vencer) + pruebas
- [x] T5: Abrir turno y turno actual (sin esperado) + prueba de concurrencia de apertura
- [x] T6: Retiros, gastos e ingresos con autorización y aviso de tope + pruebas
- [x] T7: Cierre ciego con esperado y diferencia por medio + pruebas de cálculo
- [x] T8: Aprobación de diferencias (nadie aprueba lo suyo) y tiquete Z + pruebas
- [x] T9: Pruebas de fuga de turnos, movimientos y autorizaciones
- [x] T10: Pantalla Cajas y turnos, configuración de cajas y diálogo de PIN + pruebas Vitest
- [x] T11: Regenerar tipos y sembrar una caja y un supervisor con PIN en la demo
- [x] T12: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas de la construcción (2026-11-19):
- V33: `caja`, `turno_caja` (un abierto por caja y por cajero con columnas generadas y `UNIQUE`), `movimiento_caja`
  (inmutable, monto con signo), `autorizacion_supervisor` (solo el SHA-256 del token), PIN en `usuario` y
  `empresa.tolerancia_diferencia_caja` (2.000 por defecto, D6).
- Paquete `caja/`: `CajaController` (`/api/cajas`, `/api/cajas/configuracion`), `TurnoController` (`/api/turnos`),
  `AutorizacionController` (`/api/autorizaciones`, `/api/autorizaciones/mi-pin`); `CalculoCaja` hace las cuentas en Java.
- `/api/autorizaciones` entra al límite de intentos por IP de `RateLimitFilter`; el PIN se bloquea 15 minutos tras 5
  fallos y los fallos se guardan aunque la petición falle.
- Aviso `TURNO_DIFERENCIA` a supervisores y al dueño cuando una diferencia pasa la tolerancia.
- Frontend: `/app/caja` (turnos con tiquete Z y aprobación, cajas y tolerancia, Mi PIN) y `AutorizacionPin`, reutilizable
  por el POS (005) y la anulación (006). Textos de auditoría y ruta del aviso al día.
- Ajustes frente al plan original: sin sede por usuario (el cajero ve todas las cajas activas), la autorización la pide
  la sesión del cajero, y la foto del recibo del gasto pasa a la spec 005.
- `scripts/sembrar_demo.py` crea una caja y el PIN del supervisor (queda en el archivo local de credenciales); no se
  ejecutó sobre la demo ya sembrada.
- Pruebas: CalculoCajaTest (incluye propiedades: caja esperada = suma de movimientos) y una prueba de integración y fuga
  con apertura, apertura simultánea, PIN, autorización de un solo uso, retiro, cierre ciego, aprobación y bloqueo del PIN.
