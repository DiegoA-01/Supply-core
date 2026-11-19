# Plan 004

## Datos (migración: la siguiente libre, reservarla en `agents/specs/README.md`)
**`venta`**: `id, empresa_id, consecutivo, caja_id, turno_id, bodega_id, cliente_id, vendedor_usuario_id,
cajero_usuario_id, lista_precio_id, estado (CONFIRMADA, ANULADA), subtotal, descuento_total, impuestos, total,
redondeo, cambio, es_credito, clave_idempotencia, confirmada_en, anulada_en, anulada_por_usuario_id,
autorizada_por_usuario_id, motivo_anulacion, version`.
- `consecutivo` por caja con su prefijo (`C1-000123`), único por empresa, sin huecos: contador por caja bloqueado con
  `FOR UPDATE` dentro de la misma transacción.
- `clave_idempotencia` única por empresa: si la red falla y la pantalla reenvía, **no se cobra dos veces**.

**`venta_linea`** (foto fija): `id, venta_id, producto_id, unidad_medida_id, cantidad, cantidad_unidad_producto,
precio_unitario, precio_lista, descuento_pct, descuento_valor, tarifa_impuesto_pct, base_gravable, impuesto,
total_linea, costo_unitario, autorizado_por_usuario_id`.

**`venta_pago`**: `id, venta_id, medio (EFECTIVO, TARJETA_DEBITO, TARJETA_CREDITO, TRANSFERENCIA, BILLETERA, CREDITO),
monto, recibido (solo efectivo), referencia (autorización o número de transferencia), ultimos4`.
- **Nunca** se guarda el número completo de una tarjeta ni el CVV.

**`pedido_venta`** y **`pedido_venta_linea`**: lo arma el vendedor sin cobrar (`consecutivo PV`, cliente, líneas,
descuento pedido, estado PENDIENTE_COBRO, COBRADO o DESCARTADO). No toca stock ni caja; el cajero lo carga en el POS y
la venta se recalcula al cobrar.

**Configuración por empresa:** `descuento_maximo_pct` por rol (tabla `limite_descuento_rol`).

**Inventario:** la venta usa el `SALIDA_VENTA` que ya existe, con `referencia_tipo = 'VENTA'`.

## Flujo de la venta
1. El cajero escanea o busca productos (`GET /api/ventas/productos`, spec 001) con la bodega de su caja y la lista del
   cliente.
2. Se arma el carrito **en la pantalla**. Mientras se arma no se toca ni el stock ni la caja.
3. Al cobrar, la pantalla manda: líneas (producto, unidad, cantidad, descuento pedido), cliente, pagos, el token de
   autorización si lo hay y una **clave de idempotencia** generada al abrir el carrito (cabecera `Idempotency-Key`).
4. En el backend, **en una sola transacción**:
   1. Valida turno abierto del cajero, módulo, rol y que el cliente sea de la empresa.
   2. Si la clave de idempotencia ya existe, devuelve la venta ya creada (no hace nada más).
   3. **Bloquea** las filas de stock de los productos en orden de id (`SELECT … FOR UPDATE`, evita bloqueos cruzados).
   4. Recalcula **todo** en Java: precio vigente, conversión de unidad, descuento, base, impuesto, totales. Ignora
      cualquier monto que mande la pantalla.
   5. Revisa los límites: descuento mayor al del rol o precio bajo el mínimo → exige autorización de supervisor válida
      (spec 003), que se consume aquí.
   6. Revisa los pagos: la suma debe cubrir el total; solo el efectivo puede pasarse (el exceso es el **cambio**).
      Crédito solo si el cliente tiene cupo disponible (desde la 007; antes se rechaza).
   7. Revisa el stock: si un producto no alcanza, **rechaza toda la venta** (`STOCK_INSUFICIENTE`, dice cuál).
   8. Guarda la venta, las líneas y los pagos; asigna el consecutivo.
   9. Registra un `SALIDA_VENTA` por línea (por `MovimientoInventarioService`, nunca tocando el stock a mano).
   10. Registra un `movimiento_caja` tipo `VENTA` por cada medio de pago.
   11. Audita `VENTA_CONFIRMAR`.
5. Devuelve la venta con el tiquete listo para imprimir.

**Redondeo (D3):** si se paga todo en efectivo, el total se redondea a $50 y la diferencia queda en `redondeo`.

**Descuentos:** límite por rol configurable por empresa (ej.: cajero 5 %, supervisor 15 %). Por encima del límite o por
debajo del precio mínimo: autorización de supervisor con PIN. El reporte de descuentos (spec 009) muestra quién los dio
y quién los autorizó.

## API (`@RequiereModulo(VENTAS)`; la caja exige además `CAJA`)

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `POST /api/ventas` | CAJERO, SUPERVISOR_VENTAS | Confirmar venta (con `Idempotency-Key`) |
| `GET /api/ventas`, `GET /api/ventas/{id}` | según rol (cajero su turno, vendedor sus ventas, supervisor, dueño y auditor todas) | Historial y detalle |
| `POST/GET /api/pedidos-venta`, `PUT /api/pedidos-venta/{id}` | VENDEDOR, CAJERO, SUPERVISOR_VENTAS | Pedido de venta sin cobrar |
| `GET/PUT /api/ventas/limites-descuento` | ADMIN_SISTEMA | Límite de descuento por rol |

`/api/ventas/{id}` usa `{id:\d+}` para no chocar con `/api/ventas/precios`, `/api/ventas/listas` y las demás rutas fijas
de la spec 001. Validación: cantidades > 0, máximo 300 líneas, descuento ≤ 100 %, montos con 2 decimales.

## Pruebas
- Unitarias: totales, impuestos con y sin IVA incluido, descuentos, redondeo a $50, cambio, reglas de autorización.
- Integración (MySQL real): venta completa en una transacción; idempotencia (dos envíos ⇒ una venta); rechazo por stock
  sin dejar nada a medias; consecutivo sin huecos tras un rechazo.
- Concurrencia: dos ventas simultáneas del último producto ⇒ una pasa, la otra `STOCK_INSUFICIENTE`; stock nunca
  negativo.
- Propiedades de dinero: para muchas ventas aleatorias, total = Σ líneas; Σ pagos − cambio = total; caja esperada =
  Σ movimientos.
- Fuga: ventas, líneas, pagos y pedidos de A invisibles para B; B no puede vender un producto ni un cliente de A.
- Cobertura de los Services de venta ≥ 90 %.
