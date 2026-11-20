# 007 - Crédito y cartera

**Estado:** Hecha (V37) · **Rama:** `feature/ventas-pos` (con 004 a 008) · **Depende de:** 004 (y 002 por los cupos)

## Objetivo
Vender a crédito a clientes conocidos, sin pasar su cupo, cobrar los abonos por la caja y saber quién debe y desde
cuándo. La primera versión de ventas sale solo con contado (D4); esta spec suma el crédito.

## Historias
- Como cajero, quiero vender a crédito a un cliente identificado si tiene cupo disponible.
- Como cajero, quiero recibir un abono de un cliente en mi turno y entregarle un recibo.
- Como cliente, quiero que mi abono se aplique a mi cuenta más antigua.
- Como cliente, quiero usar mi saldo a favor en una compra.
- Como supervisor o dueño, quiero ver los saldos y las edades de la cartera: al día, 1–30, 31–60, 61–90 y más de 90
  días.
- Como dueño, quiero que un cliente con mora no pueda comprar a crédito.
- Como dueño, quiero que todo cambio de cupo quede registrado.

## Criterios de aceptación
- [x] Solo se vende a crédito a un cliente identificado (nunca al consumidor final) y con cupo disponible (cupo − saldo).
- [x] Un cliente con alguna cuenta vencida no compra a crédito.
- [x] Cada venta a crédito crea su cuenta por cobrar con vencimiento según el plazo del cliente.
- [x] Cada abono entra a la caja del turno, se aplica a la cuenta más antigua y deja recibo.
- [x] El saldo a favor se puede usar como medio de pago.
- [x] El reporte de edades suma lo mismo que el saldo total de la cartera.
- [x] Los abonos cuadran en el cierre de caja.
- [x] Cambiar un cupo queda en la auditoría.
- [x] Una empresa nunca ve la cartera de otra.
- [x] Todas las pruebas pasan, incluidas las de fuga.

## Fuera de alcance
- Intereses de mora, cobro jurídico, envío automático de estados de cuenta.

## Decisiones
D4 tomada (2026-11-20): el crédito entra con esta spec. Decisión de la usuaria: un cliente en mora queda bloqueado y se avisa a los encargados, pero el supervisor o el dueño puede habilitarlo hasta una fecha con motivo (excepción en `cliente`, pantalla Cartera); la excepción no salta el cupo.
