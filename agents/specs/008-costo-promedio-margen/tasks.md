# Tasks 008

- [x] T1: Reservar la migración; agregar `costo_promedio` (inicializado) y `costo_unitario`
- [x] T2: Cálculo del costo promedio ponderado en la recepción + pruebas unitarias
- [x] T3: Costo unitario en cada movimiento de inventario + pruebas
- [x] T4: Costo en la línea de venta y margen con costo promedio + pruebas
- [x] T5: Alerta de venta bajo el costo + prueba
- [x] T6: Prueba de concurrencia de recepciones y prueba de punta a punta del margen
- [x] T7: Mostrar el costo promedio en precios y productos; regenerar tipos + pruebas Vitest
- [x] T8: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas (2026-11-20): `CalculoCosto` + `CostoPromedioService` (bloquea el producto con `refresh` y lee la existencia con
`FOR UPDATE`, así dos recepciones a la vez del mismo producto no se pisan: probado, 90 → 100 → 105). Vender bajo el costo
no se impide (decisión de la usuaria): avisa `PRECIO_BAJO_COSTO` a ADMIN_SISTEMA y SUPERVISOR_VENTAS con el cajero.
El valor del inventario de la pantalla de Inventario sigue con el último costo de compra.
