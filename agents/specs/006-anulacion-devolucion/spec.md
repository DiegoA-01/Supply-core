# 006 - Anulación y devolución

**Estado:** Hecha (V35) · **Rama:** `feature/ventas-pos` (con 004 a 008) · **Depende de:** 004

## Objetivo
Corregir una venta sin borrar nada: anularla si el error ocurre en el mismo turno, o recibir una devolución del cliente
cualquier día, con el stock y la caja volviendo exactos.

## Historias
- Como cajero, quiero anular una venta de mi turno abierto (error de digitación, el cliente desistió), con autorización
  del supervisor.
- Como cajero, quiero recibir una devolución buscando la venta original por consecutivo o por cliente.
- Como cajero, quiero elegir qué líneas y cuántas unidades se devuelven y en qué estado vuelve cada producto.
- Como cliente, quiero recibir el dinero en efectivo, por el mismo medio o como saldo a favor.
- Como dueño, quiero que la mercancía dañada no vuelva al stock de venta.
- Como dueño, quiero saber quién anuló o devolvió, quién lo autorizó y por qué.

## Criterios de aceptación
- [x] Solo se anulan ventas del turno abierto y con autorización de otra persona (nunca el mismo cajero).
- [x] Una venta de un turno cerrado no se anula: se ofrece la devolución.
- [x] La anulación devuelve el stock y registra la salida del dinero en la caja; la venta queda anulada con motivo y
  autorizador, y su consecutivo no se reutiliza.
- [x] Una devolución tiene su propio consecutivo, motivo, autorización y auditoría.
- [x] Nunca se devuelve más de lo vendido menos lo ya devuelto.
- [x] Producto en buen estado vuelve a la bodega de venta; dañado va a la bodega de averías o no reingresa.
- [x] El reembolso puede ser efectivo del turno actual, el mismo medio o saldo a favor del cliente.
- [x] El reporte de rotación descuenta lo anulado y lo devuelto.
- [x] Después de anular o devolver, stock y caja quedan exactos y los consecutivos no tienen huecos.
- [x] Una empresa nunca ve ni toca anulaciones o devoluciones de otra.
- [x] Todas las pruebas pasan, incluidas las de fuga.

## Fuera de alcance
- Usar el saldo a favor como medio de pago en una venta (se registra aquí; se consume con la cartera, spec 007).
