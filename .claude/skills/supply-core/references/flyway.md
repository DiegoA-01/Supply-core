# Flyway

- Carpeta: `backend/src/main/resources/db/migration/`. Nombre: `V{n}__descripcion_snake.sql` (dos guiones bajos).
- Se ejecutan en orden al arrancar la app; registro en `flyway_schema_history`.
- **Nunca editar un V ya ejecutado** (rompe el checksum). Todo cambio = nuevo archivo `V{n+1}`.
- La BD se crea sola (`createDatabaseIfNotExist=true`). Desarrollo: si una migración falla a medias, `DROP DATABASE supply_core;` y volver a arrancar. Nunca hacer eso con datos reales.
- Si la BD ya tenía tablas creadas a mano: borrarla (o `baseline-on-migrate`, no recomendado aquí).
- Estado actual: V1 esquema (46 tablas), V2 semillas, V3 tabla `consecutivo`, V4 estado de proveedor + categorías demo, V5 magnitud y activo en unidades, V6 saldo destino en traslados, V7 eventos de `config_notificacion`, V8 monto/política/envío en `solicitud_compra` + eventos de solicitud, V9 `rfq.abierta_en/cerrada_en` + índices + eventos de RFQ, V10 `despacho` + `despacho_linea` + tipos SALIDA_VENTA/ANULACION_DESPACHO + consecutivo DES, V11 restricciones de `cotizacion_linea` (UNIQUE cotización+línea RFQ, CHECKs) + evento COTIZACION_RECIBIDA. Próximo número libre: V12.
- Tras crear una migración: arrancar la app, verificar `success=1`, y actualizar `modelo-datos.md` si cambian tablas/enums.
