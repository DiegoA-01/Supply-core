# Plan 006

## Datos (migración: la siguiente libre, reservarla en `agents/specs/README.md`)
**Inventario:** dos tipos nuevos en `movimiento_inventario` (entradas): **`ANULACION_VENTA`** (deshace una venta del
turno, igual que `ANULACION_DESPACHO` deshace un despacho) y **`DEVOLUCION_CLIENTE`** (el cliente regresa mercancía).
Se agregan a `TipoMovimiento` y al `CHECK`/enum de la columna si lo tiene.

**`devolucion_venta`** y **`devolucion_venta_linea`**: `consecutivo, venta_id, turno_id, motivo, estado_producto
(BUENO → vuelve a la bodega de venta; DANADO → bodega de averías o sin reingreso), forma_reembolso (EFECTIVO,
MISMO_MEDIO, SALDO_A_FAVOR), total_reembolso, autorizado_por_usuario_id`; cada línea con `venta_linea_id`, cantidad y
el valor tomado de la foto fija de la línea.
- No se devuelve más de lo vendido (suma de devoluciones ≤ cantidad de la línea), validado con la línea bloqueada.

**`saldo_favor_cliente`**: movimientos del saldo a favor por cliente (se consume en la 007).

**`empresa.bodega_averias_id`** opcional.

## Flujos
**Anular una venta**
- Solo ventas del **turno abierto** y solo con autorización de supervisor (nunca el mismo cajero).
- Reintegra el stock (`ANULACION_VENTA` con referencia a la venta) y registra en la caja `ANULACION` en negativo.
- La venta queda `ANULADA` con motivo y quién autorizó. Su consecutivo no se reutiliza.
- Si la venta ya está en un turno cerrado, no se anula: se hace una **devolución**.
- Audita `VENTA_ANULAR`.

**Devolución de cliente**
1. Se busca la venta original por consecutivo o por cliente.
2. Se eligen las líneas y las cantidades (nunca más de lo vendido menos lo ya devuelto).
3. Por cada producto se marca su estado: **bueno** (vuelve al stock) o **dañado** (va a la bodega de averías o no
   reingresa).
4. Forma de reembolso: efectivo del turno actual (`movimiento_caja` `DEVOLUCION` en negativo), el mismo medio o **saldo
   a favor** del cliente.
5. Autorización de supervisor, consecutivo propio, auditoría `VENTA_DEVOLVER`.

**Rotación:** `RotacionService` pasa a restar también `ANULACION_VENTA` y `DEVOLUCION_CLIENTE` de las salidas.

## API

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `POST /api/ventas/{id}/anular` | CAJERO + autorización | Anular en el turno |
| `POST /api/devoluciones` | CAJERO + autorización | Devolución de cliente |
| `GET /api/devoluciones`, `GET /api/devoluciones/{id}` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Historial |

## Frontend
- En **Ventas** (`/app/ventas`): botones Anular (solo turno abierto) y Devolver, con el diálogo de PIN.
- Formulario de devolución: líneas con cantidad máxima devolvible, estado del producto y forma de reembolso.

## Pruebas
- Unitarias: cantidad devolvible; reembolso; nadie autoriza lo suyo.
- Integración: anular deja stock y caja como antes de la venta; devolución parcial dos veces no supera lo vendido;
  producto dañado no vuelve a la bodega de venta; consecutivos sin huecos.
- Concurrencia: dos devoluciones simultáneas de la misma línea no superan lo vendido.
- Rotación: anuladas y devueltas no cuentan como salida.
- Fuga: anular o devolver una venta de otra empresa ⇒ 404.
- Frontend: Anular oculto en turnos cerrados; la cantidad no pasa el máximo.
