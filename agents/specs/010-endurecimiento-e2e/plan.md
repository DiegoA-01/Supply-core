# Plan 010

## Alertas
- `empresa.umbral_anulaciones_dia` y `umbral_descuentos_dia` (por cajero).
- Se revisan al anular y al confirmar una venta con descuento autorizado; al pasar el umbral, evento
  `ALERTA_CAJERO` al ADMIN_SISTEMA (una vez por cajero y día).

## Pruebas de punta a punta (Playwright)
- Carpeta `e2e/` con Playwright (dependencia nueva: pedir el visto bueno).
- Recorrido: abrir turno → vender → anular con PIN → cerrar turno, contra el stack de Docker.
- Trabajo `e2e` en `.github/workflows/ci.yml` (definido en la spec 000): `docker compose up` con un `.env` de prueba
  generado en el job → siembra la demo (`scripts/sembrar_demo.py`) → Playwright. Las claves salen del archivo de
  credenciales que genera la siembra dentro del job; nunca se suben.

## Prueba de carga
- Script con k6 (o JMeter) fuera del CI de cada PR: 50 ventas por minuto durante 5 minutos en varias cajas; al final se
  comprueban stock no negativo, consecutivos sin huecos y caja esperada = Σ movimientos.

## Pruebas
- Unitarias de los umbrales.
- El propio trabajo `e2e` en verde en el CI.
- Reporte de la prueba de carga guardado como artefacto.
- Revisión de cobertura con JaCoCo (≥ 90 % en los Services de ventas y caja).
