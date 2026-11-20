# Tasks 006

- [x] T1: Reservar la migración; tipos `ANULACION_VENTA` y `DEVOLUCION_CLIENTE`, tablas de devolución, saldo a favor y bodega de averías
- [x] T2: Anular venta en el turno abierto (stock, caja, auditoría) + pruebas
- [x] T3: Devolución de cliente con estado del producto y reembolso + pruebas
- [x] T4: Prueba de concurrencia de devoluciones de la misma línea
- [x] T5: Rotación que resta anulaciones y devoluciones + prueba
- [x] T6: Pruebas de fuga de anulaciones y devoluciones
- [x] T7: Botones y formulario en la pantalla de Ventas + pruebas Vitest
- [x] T8: Regenerar tipos y ampliar la demo
- [x] T9: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas (2026-11-20): pruebas en `AislamientoEmpresasTest.anularYDevolverDejanStockYCajaExactosSinPasarLoVendidoNiCruzarEmpresas`
(anular, devoluciones parciales, tope de lo devolvible, dañado sin reingreso, saldo a favor, dos devoluciones a la vez,
turno cerrado, fugas), `DevolucionServiceTest` y `RotacionServiceTest`. La prueba de concurrencia encontró un fallo real:
la devolución debe bloquear la venta antes de cualquier otra lectura (lectura repetible de MySQL). Probado en la demo
local desde la pantalla: devolución con PIN (C1-000003) y anulación con PIN (C1-000004).
