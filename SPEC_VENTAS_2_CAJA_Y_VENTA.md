# Spec de Ventas y Caja · Parte 2: caja y venta en mostrador (F3 a F6)

**Estado:** propuesta para revisar. No hay código todavía.
**Parte de:** [SPEC_VENTAS.md](SPEC_VENTAS.md) (índice). Necesita la parte 1 terminada (roles, módulos, precios, impuestos y clientes).
**Fecha:** 2026-11-18.

---

> Aplican sin cambios las **reglas de oro**, el **glosario**, los **roles**, la **seguridad** y la **estrategia de
> pruebas** de la [parte 1](SPEC_VENTAS_1_BASE_Y_CATALOGO.md).

## 6. Modelo de datos (parte 2)

### 6.3 Cajas y turnos

**`caja`**: `id, empresa_id, sede_id, bodega_id, nombre, prefijo_consecutivo, tope_efectivo, activa`.

**`turno_caja`**: `id, empresa_id, caja_id, cajero_usuario_id, estado (ABIERTO, CERRADO, APROBADO), abierto_en,
base_apertura, cerrado_en, contado_efectivo, contado_otros (JSON por medio), esperado_efectivo, diferencia,
observacion_cierre, aprobado_por_usuario_id, aprobado_en, version`.
- **Un solo turno abierto por caja y uno por cajero** (índice único parcial vía columna generada).
- `esperado_*` y `diferencia` los calcula Java al cerrar; se guardan para que el histórico no cambie.

**`movimiento_caja`**: libro de la caja. **Cada peso que entra o sale queda aquí.**
`id, empresa_id, turno_id, tipo (BASE, VENTA, DEVOLUCION, ANULACION, RETIRO, GASTO, INGRESO, ABONO_CARTERA),
medio_pago, monto (positivo entra, negativo sale), referencia_tipo, referencia_id, motivo, usuario_id,
autorizado_por_usuario_id, creado_en`.

### 6.4 Ventas

**`venta`**: `id, empresa_id, consecutivo, caja_id, turno_id, bodega_id, cliente_id, vendedor_usuario_id,
cajero_usuario_id, lista_precio_id, estado (CONFIRMADA, ANULADA), subtotal, descuento_total, impuestos, total,
redondeo, cambio, es_credito, clave_idempotencia, confirmada_en, anulada_en, anulada_por_usuario_id,
autorizada_por_usuario_id, motivo_anulacion, version`.
- `consecutivo` por caja con su prefijo (`C1-000123`), único por empresa, sin huecos.
- `clave_idempotencia` única por empresa: si la red falla y la pantalla reenvía, **no se cobra dos veces**.

**`venta_linea`** (foto fija): `id, venta_id, producto_id, unidad_medida_id, cantidad, cantidad_unidad_producto,
precio_unitario, precio_lista, descuento_pct, descuento_valor, tarifa_impuesto_pct, base_gravable, impuesto,
total_linea, costo_unitario, autorizado_por_usuario_id`.

**`venta_pago`**: `id, venta_id, medio (EFECTIVO, TARJETA_DEBITO, TARJETA_CREDITO, TRANSFERENCIA, BILLETERA, CREDITO),
monto, recibido (solo efectivo), referencia (autorización o número de transferencia), ultimos4`.
- **Nunca** se guarda el número completo de una tarjeta ni el CVV.

**`devolucion_venta`** y **`devolucion_venta_linea`**: `consecutivo, venta_id, turno_id, motivo, estado_producto
(BUENO → vuelve a la bodega de venta; DANADO → bodega de averías o sin reingreso), forma_reembolso (EFECTIVO,
MISMO_MEDIO, SALDO_A_FAVOR), total_reembolso, autorizado_por_usuario_id`.
- No se devuelve más de lo vendido (suma de devoluciones ≤ cantidad de la línea).

### 6.6 Inventario

Dos tipos nuevos en `movimiento_inventario` (entradas): **`ANULACION_VENTA`** (deshace una venta del turno, igual que
`ANULACION_DESPACHO` deshace un despacho) y **`DEVOLUCION_CLIENTE`** (el cliente regresa mercancía). La venta usa el
`SALIDA_VENTA` que ya existe, con `referencia_tipo = 'VENTA'`. El reporte de rotación pasa a restar también estos dos
tipos de las salidas. En F8 se agrega `costo_unitario` al movimiento para el costo promedio.

---

## 7. Flujos paso a paso

### 7.1 Abrir turno
1. El cajero elige su caja (solo las activas de su sede).
2. Cuenta la base en efectivo y la escribe.
3. Java valida: no tiene otro turno abierto y la caja tampoco.
4. Se crea el turno y un `movimiento_caja` tipo `BASE`. Se audita `TURNO_ABRIR`.

### 7.2 Vender (el corazón del módulo)
1. El cajero escanea o busca productos. La pantalla pide al backend el precio **vigente** de la lista del cliente.
2. Se arma el carrito **en la pantalla**. Mientras se arma no se toca ni el stock ni la caja.
3. Al cobrar, la pantalla manda: líneas (producto, unidad, cantidad, descuento pedido), cliente, pagos y una
   **clave de idempotencia** generada al abrir el carrito.
4. En el backend, **en una sola transacción**:
   1. Valida turno abierto del cajero, módulo, rol y que el cliente sea de la empresa.
   2. Si la clave de idempotencia ya existe, devuelve la venta ya creada (no hace nada más).
   3. **Bloquea** las filas de stock de los productos en orden de id (`SELECT … FOR UPDATE`, evita bloqueos cruzados).
   4. Recalcula **todo** en Java: precio vigente, conversión de unidad, descuento, base, impuesto, totales. Ignora
      cualquier monto que mande la pantalla.
   5. Revisa los límites: descuento mayor al del rol o precio bajo el mínimo → exige autorización de supervisor válida.
   6. Revisa los pagos: la suma debe cubrir el total; solo el efectivo puede pasarse (el exceso es el **cambio**).
      Crédito solo si el cliente tiene cupo disponible.
   7. Revisa el stock: si un producto no alcanza, **rechaza toda la venta** (`STOCK_INSUFICIENTE`, dice cuál).
   8. Guarda la venta, las líneas y los pagos; asigna el consecutivo.
   9. Registra un `SALIDA_VENTA` por línea (por `MovimientoInventarioService`, nunca tocando el stock a mano).
   10. Registra un `movimiento_caja` tipo `VENTA` por cada medio de pago.
   11. Audita `VENTA_CONFIRMAR`.
5. Devuelve la venta con el tiquete listo para imprimir.

### 7.3 Descuentos
- Límite por rol configurable por empresa (ej.: cajero 5 %, supervisor 15 %).
- Por encima del límite o por debajo del precio mínimo: autorización de supervisor con PIN.
- El reporte de descuentos muestra quién los dio y quién los autorizó.

### 7.4 Anular una venta
- Solo ventas del **turno abierto** y solo con autorización de supervisor (nunca el mismo cajero).
- Reintegra el stock (`ANULACION_VENTA` con referencia a la venta) y registra en la caja `ANULACION` en negativo.
- La venta queda `ANULADA` con motivo y quién autorizó. Su consecutivo no se reutiliza.
- Si la venta ya está en un turno cerrado, no se anula: se hace una **devolución**.

### 7.5 Devolución de cliente
1. Se busca la venta original por consecutivo o por cliente.
2. Se eligen las líneas y las cantidades (nunca más de lo vendido menos lo ya devuelto).
3. Por cada producto se marca su estado: **bueno** (vuelve al stock) o **dañado** (va a la bodega de averías o no reingresa).
4. Forma de reembolso: efectivo del turno actual, el mismo medio o **saldo a favor** del cliente.
5. Autorización de supervisor, consecutivo propio, auditoría.

### 7.6 Retiros y gastos menores
- **Retiro a bóveda:** cuando el efectivo pasa el `tope_efectivo` de la caja, la pantalla avisa que hay que retirar.
- **Gasto menor:** con motivo y soporte (foto del recibo opcional).
- Los dos con autorización de supervisor. Quedan en `movimiento_caja` en negativo.

### 7.7 Cerrar turno (cierre ciego)
1. El cajero cuenta el efectivo **sin ver cuánto debería tener** y escribe lo contado. Escribe también los totales de
   los vouchers de tarjeta y las transferencias.
2. Java calcula el esperado: `base + ventas en efectivo + abonos − devoluciones en efectivo − retiros − gastos`.
3. Guarda la diferencia por medio de pago. Turno `CERRADO`.
4. Si la diferencia pasa la tolerancia (configurable, por ejemplo $2.000), queda pendiente de aprobación de un
   supervisor, que escribe la explicación. Turno `APROBADO`.
5. El reporte del cierre (tiquete Z) se imprime y queda guardado. Audita `TURNO_CERRAR` y `TURNO_APROBAR`.

---

## 8. Controles contra pérdidas de dinero

| Riesgo | Control |
|---|---|
| Doble cobro por doble clic o red lenta | Clave de idempotencia por venta (única en la base). |
| Dos cajeros venden el último producto | Bloqueo de la fila de stock y validación dentro de la transacción; nunca stock negativo (`CHECK cantidad >= 0` ya existe). |
| La pantalla manda un precio falso | El backend ignora los montos del cliente y recalcula con el precio vigente. |
| Vender por debajo del costo | `precio_minimo` y alerta si el precio queda bajo el costo promedio (F8). |
| Descuentos "a los amigos" | Límite por rol, autorización con PIN, reporte de descuentos por cajero. |
| Anular para quedarse el efectivo | Solo en el turno abierto, con autorización de otra persona; reporte de anulaciones por cajero y alerta si pasan un umbral. |
| Faltantes de caja | Cierre ciego, diferencias guardadas por turno y por cajero, aprobación obligatoria si pasan la tolerancia. |
| Mucho efectivo en el cajón | Tope de efectivo por caja y aviso de retiro. |
| Ventas "por fuera del sistema" | Cuadre diario automático: ventas ↔ movimientos de inventario ↔ movimientos de caja. Cualquier diferencia genera alerta. |
| Reimprimir un tiquete para cobrarlo otra vez | La reimpresión dice "COPIA" y queda auditada. |
| Borrar o cambiar el historial | Ventas, turnos y movimientos sin borrado ni edición; correcciones solo por anulación o devolución. |
| Cambios de precio sin control | Historial de precios con quién, cuándo y motivo; solo roles autorizados. |
| Cajero que ve el esperado y "cuadra" | El esperado no se muestra hasta guardar lo contado. |

> El **cuadre diario automático** de ventas ↔ inventario ↔ caja se construye en la
> [parte 3](SPEC_VENTAS_3_CARTERA_COSTOS_REPORTES.md) (F9).

---

## 10. API de esta parte


| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET/POST/PUT /api/cajas` | ADMIN_SISTEMA | Cajas |
| `POST /api/turnos` | CAJERO, SUPERVISOR | Abrir turno |
| `GET /api/turnos/actual` | CAJERO, SUPERVISOR | Turno abierto del usuario |
| `POST /api/turnos/{id}/movimientos` | CAJERO + autorización | Retiro, gasto o ingreso |
| `POST /api/turnos/{id}/cerrar` | CAJERO | Cierre ciego |
| `POST /api/turnos/{id}/aprobar` | SUPERVISOR, ADMIN_SISTEMA | Aprobar la diferencia |
| `POST /api/ventas` | CAJERO, SUPERVISOR | Confirmar venta (con `Idempotency-Key`) |
| `GET /api/ventas`, `GET /api/ventas/{id}` | según rol | Historial y detalle |
| `POST /api/ventas/{id}/anular` | CAJERO + autorización | Anular en el turno |
| `GET /api/ventas/{id}/tiquete` | según rol | Tiquete (reimpresión marcada COPIA) |
| `POST /api/devoluciones` | CAJERO + autorización | Devolución de cliente |
| `POST /api/autorizaciones` | SUPERVISOR | Valida PIN y devuelve un permiso de un solo uso (2 min) para una acción concreta |

**Autorización de un solo uso:** el supervisor escribe su PIN y el backend devuelve un token corto (2 minutos, una sola
acción, ligado al turno y a la venta). La acción del cajero lo consume. Así el PIN no viaja en cada petición y no se
puede reutilizar.

---

## 11. Pantallas de esta parte


| Pantalla | Para quién | Lo principal |
|---|---|---|
| **POS** (`/app/pos`) | Cajero | Campo con foco permanente para el lector (Enter agrega), atajos de teclado (F2 buscar, F4 cliente, F9 cobrar, Esc cancelar), carrito con cantidades editables, totales que vienen del servidor, cobro con pagos mixtos y cálculo del cambio por el backend, aviso de tope de efectivo |
| **Tiquete** | Cajero | Formato 80 mm para impresora térmica (CSS de impresión), con consecutivo, NIT, impuestos discriminados y "COPIA" en reimpresiones |
| **Mi turno** | Cajero | Abrir, retiros y gastos, cierre ciego |
| **Ventas** | Supervisor, dueño | Historial con filtros, detalle, anular y devolver |
| **Cajas y turnos** | Supervisor, dueño | Turnos abiertos, cierres, diferencias por aprobar |

Reglas de siempre: la UI nunca calcula dinero ni stock; tipos generados del Swagger; pruebas Vitest por pantalla.

---

## 13. Pruebas que exige esta parte


| Nivel | Qué cubre |
|---|---|
| **Unitarias** (JUnit + Mockito) | Cálculo de totales, impuestos (precio con y sin IVA incluido), descuentos, redondeo, cambio, esperado de caja, reglas de autorización |
| **Integración con MySQL real** (`supply_core_test`) | Venta completa en una transacción; idempotencia; rechazo por stock; anulación y devolución reintegran stock y caja |
| **Fuga entre empresas** (`AislamientoEmpresasTest`) | Una prueba por cada tabla y endpoint nuevo |
| **Concurrencia** | Dos ventas simultáneas del último producto: una pasa y la otra recibe `STOCK_INSUFICIENTE`; el stock nunca queda negativo |
| **Propiedades de dinero** | Para muchas ventas aleatorias: total = Σ líneas; Σ pagos − cambio = total; caja esperada = Σ movimientos |
| **Frontend** (Vitest) | POS, cobro, tiquete, cierre ciego (no muestra el esperado antes de guardar), autorización |

**Meta de cobertura** en los Services de ventas y caja: 90 % de líneas (medida con JaCoCo en CI).

---

## 15. Fases de esta parte


| Fase | Rama | Entrega | Listo cuando |
|---|---|---|---|
| **F3** | `feature/caja-turnos` | Cajas, turnos, movimientos de caja, PIN de supervisor y autorizaciones de un solo uso, cierre ciego, aprobación | Esperado y diferencias calculados en Java; no se muestra el esperado antes de contar |
| **F4** | `feature/ventas-pos-backend` | Venta en una transacción: precios, descuentos, impuestos, pagos mixtos, cambio, stock, caja, idempotencia | Pruebas de concurrencia, idempotencia y propiedades de dinero en verde |
| **F5** | `feature/ventas-pos-frontend` | POS con lector y atajos, cobro, tiquete 80 mm, mi turno | Venta completa desde la pantalla en Docker |
| **F6** | `feature/ventas-anulacion-devolucion` | Anulación en turno y devoluciones con estado del producto y reembolso | Stock y caja vuelven exactos; consecutivos sin huecos |

Cada fase deja: código, pruebas (incluida la de fuga), Javadoc completo, tipos del frontend regenerados, `DOCKER.md` o la
skill actualizados si cambia algo de operación, y la siembra de la demo ampliada para poder probarla.

---

## 16. Decisiones de esta parte


| # | Pregunta | Recomendación |
|---|---|---|
| D1 | ¿Se permite vender sin stock (stock negativo)? | **No.** Es la principal fuente de descuadres. |
| D3 | ¿Redondear el total en efectivo a $50? | **Sí**, guardando la diferencia en `redondeo` para que cuadre. |
| D6 | ¿Tolerancia de diferencia de caja sin aprobación? | $2.000, configurable por empresa. |
| D7 | ¿Una caja puede descontar de varias bodegas? | **No**: una caja, una bodega. Los traslados ya existen. |

---

## 17. Riesgos de esta parte


| Riesgo | Cómo se mitiga |
|---|---|
| El POS debe ser muy rápido | Búsqueda indexada por código y código de barras; el carrito vive en la pantalla y solo hay una petición al cobrar. |
| Sin internet no se puede vender | Fuera de esta spec. Se documenta como limitación; un modo sin conexión sería una spec aparte. |
| Bloqueos de la base con muchas cajas | Bloqueo solo de las filas de stock de la venta, en orden de id, y transacciones cortas. |
