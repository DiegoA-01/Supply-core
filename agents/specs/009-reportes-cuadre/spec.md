# 009 - Reportes de ventas y caja, tablero y cuadre diario

**Estado:** Hecha (2026-11-20, V38) · **Rama:** `feature/ventas-reportes` · **Depende de:** 006, 008

## Objetivo
Que el dueño vea cómo van las ventas y la caja sin pedirle nada a nadie, y que el sistema detecte solo cualquier venta
"por fuera" o cualquier descuadre entre ventas, inventario y caja.

## Historias
- Como dueño, quiero ver las ventas por día, semana y mes, y por producto, categoría, cliente, vendedor, cajero, caja y
  medio de pago.
- Como dueño, quiero ver el margen por producto y por categoría.
- Como dueño o supervisor, quiero ver los cierres por turno, las diferencias por cajero, los retiros, los gastos, las
  anulaciones y los descuentos autorizados.
- Como dueño, quiero un tablero con las ventas de hoy y del mes, el ticket promedio, el margen, los productos más
  vendidos, las ventas por medio de pago y por cajero, y las diferencias de caja.
- Como dueño, quiero que cada noche el sistema compare ventas, inventario y caja, y me avise si algo no cuadra.
- Como dueño, quiero exportar los reportes a Excel y PDF.
- Como dueño, quiero que el informe de compras que me envía el jefe de compras pueda traer también un resumen de ventas.

## Criterios de aceptación
- [x] Los reportes de ventas y de caja filtran por fecha y por las dimensiones de la historia, y sus totales coinciden
  con las ventas y movimientos guardados.
- [x] El margen usa el costo guardado en cada línea.
- [x] El tablero de ventas está en el mismo tablero del dueño que ya existe, no en otra pantalla.
- [x] El cuadre diario revisa: total de ventas confirmadas = ventas registradas en caja; cantidades vendidas = salidas de
  inventario por venta; turnos abiertos por más de 24 horas.
- [x] Un descuadre provocado en la prueba se detecta, se avisa al dueño y queda en un reporte.
- [x] Las exportaciones reutilizan los mismos datos de la pantalla.
- [x] Cajero ve su turno, vendedor sus ventas; supervisor, dueño y auditor todo.
- [x] Una empresa nunca ve reportes de otra.
- [x] Todas las pruebas pasan, incluidas las de fuga.

## Decisiones (2026-11-20)
- Las ventas se reportan **netas**: confirmadas menos devoluciones; las anuladas no cuentan.
- El cuadre automático corre a la **01:00** y revisa el día anterior completo.
- Los reportes se exportan en **Excel y PDF**.
- El informe de compras trae el resumen de ventas **solo si la empresa tiene el módulo de ventas**.

## Fuera de alcance
- Alertas por cajero con umbral y prueba de carga (spec 010).
