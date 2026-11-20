# Tasks 007

- [x] T1: Reservar la migración; crear `cuenta_por_cobrar` y `abono_cliente`
- [x] T2: Medio `CREDITO` en la venta con cupo y mora + pruebas (incluida la concurrencia del cupo)
- [x] T3: Abonos aplicados a la cuenta más antigua, en la caja, con recibo + pruebas
- [x] T4: Saldo a favor como medio de pago + pruebas
- [x] T5: Cartera con edades + pruebas de propiedades
- [x] T6: Anular una venta a crédito anula su cuenta + prueba
- [x] T7: Pruebas de fuga de cartera
- [x] T8: Pantalla Cartera, crédito en el POS y abono en Mi turno + pruebas Vitest
- [x] T9: Regenerar tipos y ampliar la demo con un cliente a crédito
- [x] T10: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas (2026-11-20): prueba de punta a punta en `AislamientoEmpresasTest.laVentaACreditoRespetaCupoYMoraYLosAbonosEntranALaCajaSinCruzarEmpresas`
(consumidor final, cupo, dos ventas a crédito a la vez, abono y recibo, mora con aviso que queda aunque la venta se
rechace, excepción, edades, anulación con y sin abonos, saldo a favor como medio, fugas) y `CalculoCarteraTest`. El cliente
se bloquea con `refresh` y los saldos se leen con `FOR UPDATE`. Probado en la demo: venta a crédito C1-000005 y abono
AB-000001 desde Mi turno con su recibo.
