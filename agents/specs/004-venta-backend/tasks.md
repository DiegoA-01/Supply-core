# Tasks 004

- [x] T1: Reservar la migración; crear `venta`, `venta_linea`, `venta_pago`, contador por caja y límites de descuento
- [x] T2: Cálculo de la venta en Java (precio vigente, unidad, descuento, base, impuesto, totales, redondeo, cambio) + pruebas unitarias
- [x] T3: Reglas de descuento y precio mínimo con autorización de un solo uso + pruebas
- [x] T4: Confirmar venta en una transacción (bloqueo de stock, salida de inventario, movimiento de caja, auditoría) + prueba de integración
- [x] T5: Idempotencia con `Idempotency-Key` + prueba de doble envío
- [x] T6: Prueba de concurrencia del último producto
- [x] T7: Pruebas de propiedades de dinero
- [x] T8: Historial y detalle de ventas por rol + pruebas
- [x] T9: Pedido de venta sin cobrar + pruebas
- [x] T10: Pruebas de fuga de ventas, pagos y pedidos
- [x] T11: Regenerar tipos; revisar cobertura ≥ 90 % en JaCoCo
- [x] T12: Verificar los criterios de aceptación y que `./mvnw test` pase

Notas (2026-11-20): la prueba de punta a punta, la de idempotencia, la del último producto en dos cajas y las de fuga
están en `AislamientoEmpresasTest.laVentaCobraEnUnaTransaccionSinDuplicarNiVenderSinStockYNoCruzaEmpresas`; las de
cálculo y propiedades del dinero (2.000 ventas aleatorias) en `CalculoVentaTest`.
