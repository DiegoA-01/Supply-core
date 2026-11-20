# Tasks 005

- [x] T1: `POST /api/ventas/cotizar` sin guardar nada + pruebas
- [x] T2: `GET /api/ventas/{id}/tiquete` con COPIA y auditoría + pruebas + fuga
- [x] T3: Pantalla Mi turno (abrir, retiros, gastos, cierre ciego) + pruebas Vitest
- [x] T4: POS: lector con foco, búsqueda y carrito + pruebas Vitest
- [x] T5: POS: atajos de teclado, cliente y carga de pedidos de venta + pruebas
- [x] T6: POS: cobro con pagos mixtos, cambio, autorización y reintento idempotente + pruebas
- [x] T7: Tiquete de 80 mm con CSS de impresión + prueba
- [x] T8: Historial y detalle de ventas + pruebas
- [~] T9: Probar una venta completa en Docker y ajustar la demo
- [x] T10: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas (2026-11-20): T9 se probó en la demo local (`ng serve` + perfil `demo`): turno abierto, venta con lector y efectivo
(C1-000001), venta con descuento autorizado por PIN (C1-000002, redondeo $20), reimpresión con COPIA y cierre ciego
que cuadró exacto. Falta repetirla en el stack de Docker. El soporte del gasto es texto (número del recibo); la foto del
recibo queda pendiente.
