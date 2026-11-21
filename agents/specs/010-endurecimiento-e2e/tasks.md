# Tasks 010

- [x] T1: Umbrales de anulaciones y descuentos por cajero con alerta al dueño + pruebas
- [x] T2: Playwright (con visto bueno) y recorrido abrir → vender → anular con PIN → cerrar
- [~] T3: Trabajo `e2e` en el CI con `.env` generado y siembra de la demo
- [x] T4: Prueba de carga de 50 ventas por minuto con sus comprobaciones
- [x] T5: Subir la cobertura de los Services de ventas y caja a 90 % donde falte
- [x] T6: Verificar los criterios de aceptación y que el CI completo pase

Notas (2026-11-20): decisiones de la usuaria: Playwright como dependencia de desarrollo en `e2e/`, prueba de carga en
Python sin dependencias (`scripts/prueba_carga.py`), umbrales por defecto 3 anulaciones y 5 descuentos por cajero y día,
aviso al dueño y a los supervisores. La prueba de Playwright pasó contra la app local (`ng serve` + perfil demo); la de
carga, 50 ventas en 1 minuto en 3 cajas: 0 errores, stock exacto, sin huecos, cajas cuadradas, 80 ms de media. T3: el
trabajo `e2e` de `ci.yml` y el workflow manual `carga.yml` están escritos; Docker Desktop no arrancó en el equipo, así
que falta verlos correr (en GitHub o en local). La 009 tenía records anidados con nombres repetidos (`Totales`, `Fila`…)
que chocaban en el Swagger: se les dio nombre único con `@Schema` para que `npm run api:tipos` coincida con los tipos.
La prueba de cobertura encontró un fallo de la 001: una fila de la carga de precios con una unidad que no se convierte
hacía fallar todo el archivo con 500 (la validación llamaba a un método transaccional que marcaba la transacción para
deshacerse). `PreciosService.unidadDelPrecio` compara ahora la magnitud directamente, con el mismo error.
Verificado en Docker local (2026-11-20, mismos pasos que el CI): API lista con la espera del CI, siembra, Playwright
pasa (9,4 s) y carga completa 250 ventas en 5 min en 3 cajas: 0 errores, 50,2/min, media 81 ms, p95 115 ms, stock
exacto, sin huecos, cajas cuadradas. La carga ahora espera y reintenta el ingreso ante el límite de intentos por minuto
por IP (429). Solo falta ver el YAML de `ci.yml` y `carga.yml` correr en GitHub.
