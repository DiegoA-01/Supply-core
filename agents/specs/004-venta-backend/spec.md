# 004 - La venta (el corazón del módulo)

**Estado:** Hecha (V34) · **Rama:** `feature/ventas-pos` (con 004 a 008) · **Depende de:** 002, 003

## Objetivo
Que cobrar una venta descuente el stock, registre el dinero en la caja y deje una foto fija de precios, impuestos,
descuentos y costos, todo de una sola vez: o pasa completa o no pasa nada.

## Historias
- Como cajero, quiero cobrar una venta con uno o varios medios de pago (efectivo, débito, crédito, transferencia,
  billetera digital) y que el sistema me diga el cambio.
- Como cajero, quiero que si la red falla y la pantalla reenvía, no se cobre dos veces.
- Como cajero, quiero dar un descuento hasta el límite de mi rol y pedir autorización al supervisor si necesito más o si
  el precio queda por debajo del mínimo.
- Como vendedor, quiero armar un pedido de venta sin cobrar, para que el cajero lo cobre después.
- Como dueño, quiero que nunca se venda lo que no hay en la bodega de la caja.
- Como dueño, quiero que el precio, el impuesto, el descuento y el costo de cada línea queden como estaban el día de la
  venta.
- Como dueño, quiero que el total en efectivo se redondee a $50 sin descuadrar la caja (D3).
- Como dueño, quiero ver quién dio cada descuento y quién lo autorizó.

## Criterios de aceptación
- [x] La venta toma el precio vigente de la lista del cliente; lo que mande la pantalla como monto se ignora.
- [x] Subtotal, descuentos, base, impuestos, total, redondeo y cambio los calcula el sistema; el total cuadra con la
  suma de las líneas.
- [x] La suma de los pagos cubre el total; solo el efectivo puede pasarse y el exceso es el cambio.
- [x] Si un producto no alcanza, se rechaza toda la venta y se dice cuál (D1: nunca stock negativo).
- [x] Dos ventas simultáneas del último producto: una pasa y la otra se rechaza por stock.
- [x] La misma venta reenviada con la misma clave devuelve la venta ya creada y no cobra otra vez.
- [x] El consecutivo es por caja con su prefijo (por ejemplo `C1-000123`), único y sin huecos.
- [x] Un descuento sobre el límite del rol o un precio bajo el mínimo sin autorización válida se rechaza.
- [x] El límite de descuento por rol se configura por empresa (por ejemplo cajero 5 %, supervisor 15 %).
- [x] Cada venta deja una salida de inventario por línea y un movimiento de caja por medio de pago.
- [x] De una tarjeta solo se guardan los últimos 4 dígitos y el número de autorización.
- [x] Un pedido de venta sin cobrar no toca stock ni caja.
- [x] La venta confirmada no se puede editar ni borrar, y queda en la auditoría.
- [x] Una empresa nunca ve ni vende con datos de otra.
- [x] Pruebas de concurrencia, idempotencia y propiedades de dinero en verde, más las de fuga.

## Fuera de alcance
- La pantalla del POS y el tiquete (spec 005).
- Anular y devolver (spec 006).
- Vender a crédito (spec 007; hasta entonces el medio `CREDITO` se rechaza, D4).
- Costo promedio (spec 008): mientras tanto el costo de la línea es el último costo de compra.

## Decisiones
D1 (nunca stock negativo) y D3 (redondeo del efectivo a $50) tomadas el 2026-11-20. Límite de descuento por defecto: cajero 5 %, supervisor de ventas 15 %, vendedor 5 %; el dueño no tiene límite.
