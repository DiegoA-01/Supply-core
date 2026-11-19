# 007 - Crédito y cartera

**Estado:** Propuesta · **Rama:** `feature/ventas-cartera` · **Depende de:** 004 (y 002 por los cupos)

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
- [ ] Solo se vende a crédito a un cliente identificado (nunca al consumidor final) y con cupo disponible (cupo − saldo).
- [ ] Un cliente con alguna cuenta vencida no compra a crédito.
- [ ] Cada venta a crédito crea su cuenta por cobrar con vencimiento según el plazo del cliente.
- [ ] Cada abono entra a la caja del turno, se aplica a la cuenta más antigua y deja recibo.
- [ ] El saldo a favor se puede usar como medio de pago.
- [ ] El reporte de edades suma lo mismo que el saldo total de la cartera.
- [ ] Los abonos cuadran en el cierre de caja.
- [ ] Cambiar un cupo queda en la auditoría.
- [ ] Una empresa nunca ve la cartera de otra.
- [ ] Todas las pruebas pasan, incluidas las de fuga.

## Fuera de alcance
- Intereses de mora, cobro jurídico, envío automático de estados de cuenta.

## Decisiones
D4 recomendada (ver `agents/specs/README.md`).
