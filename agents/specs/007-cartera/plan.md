# Plan 007

## Datos (migración: la siguiente libre, reservarla en `agents/specs/README.md`)
**`cuenta_por_cobrar`**: una por venta a crédito. `id, empresa_id, venta_id, cliente_id, valor, saldo, vence_en, estado
(ABIERTA, PAGADA, ANULADA)`.

**`abono_cliente`**: `id, empresa_id, consecutivo, cliente_id, turno_id, valor, medio_pago, aplicado_a (JSON por
factura), creado_por_usuario_id, creado_en`. Entra a la caja como `ABONO_CARTERA`.

## Flujos
**Venta a crédito** (dentro de la transacción de la 004)
- Medio `CREDITO` solo con cliente identificado y cupo disponible (cupo − saldo), bloqueando las cuentas del cliente.
- Cliente con mora (alguna cuenta vencida con saldo) no compra a crédito.
- Crea la `cuenta_por_cobrar` con `vence_en = hoy + plazo_dias`.
- Anular una venta a crédito (006) anula su cuenta si no tiene abonos.

**Abono**
- Cada abono entra a la caja del turno (`movimiento_caja` `ABONO_CARTERA`), se aplica a la cuenta más antigua y deja
  recibo. El esperado del cierre ya suma los abonos (003).

**Saldo a favor**
- Medio de pago `SALDO_FAVOR` en la venta, que consume `saldo_favor_cliente` (006).

**Edades:** al día, 1–30, 31–60, 61–90, +90 días, calculadas en Java con la fecha de vencimiento.

## API (`@RequiereModulo(CARTERA)`)

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/cartera` | CAJERO, SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Saldos por cliente y edades |
| `GET /api/cartera/clientes/{id}` | los mismos | Cuentas y abonos de un cliente |
| `POST /api/abonos` | CAJERO, SUPERVISOR_VENTAS | Registrar abono en el turno |
| `GET /api/abonos/{id}/recibo` | los mismos | Recibo del abono |

## Frontend
- **Cartera** (`/app/cartera`, supervisor y dueño): saldos, edades, abonos.
- En el POS: medio Crédito y Saldo a favor; en Mi turno: registrar abono.

## Pruebas
- Unitarias: cupo disponible, mora, aplicación a la cuenta más antigua, edades.
- Integración: venta a crédito crea la cuenta; abono reduce el saldo y entra a la caja; cliente en mora rechazado;
  concurrencia de dos ventas a crédito que juntas pasan el cupo ⇒ una se rechaza.
- Propiedades: Σ edades = Σ saldos; caja esperada incluye los abonos.
- Fuga: cartera, cuentas y abonos de A invisibles para B.
- Frontend: la pantalla de cartera y el abono muestran lo que devuelve el servidor.
