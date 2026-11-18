# Spec: módulo de Ventas y Caja

**Estado:** propuesta para revisar. No hay código todavía.
**Rama:** `feature/spec-ventas`. Cada fase se construye después en su propia rama, sacada de `develop`.
**Fecha:** 2026-11-18.

---

## 1. Objetivo

Que Supply-Core también **venda**, no solo compre y guarde. El negocio (ferretería, repuestos de moto o de carro,
eléctricos, droguería…) debe poder:

- cobrar en un punto de venta (POS) rápido, con lector de código de barras;
- saber en todo momento **cuánto entró, cuánto salió y cuánto debe haber en cada caja**;
- descontar el stock en el mismo instante de la venta, sin pasos manuales;
- ver qué vendió, a quién, quién lo vendió, con qué margen y por qué medio de pago;
- no perder dinero por errores, descuidos o fraude interno, y poder demostrarlo con un historial que no se borra.

### Fuera de esta spec

| Tema | Por qué |
|---|---|
| Factura electrónica DIAN | Fase propia, después de este módulo (ya está anotada como fase futura). El modelo de venta queda listo para emitirla. |
| Pasarela de pagos o datáfono integrado | El cobro con tarjeta se **registra** (últimos 4 dígitos y número de autorización). No se procesan tarjetas. |
| Multimoneda | Sigue fuera del alcance actual. Todo en la moneda base de la empresa (COP). |
| Tienda en línea o marketplace | Fuera del alcance del proyecto. |

> **Antes de construir:** hay que actualizar `CLAUDE.md` (regla 7 y la descripción del proyecto) para que Ventas y Caja
> entren al alcance. Hoy el proyecto se describe como compras e inventario.

---

## 2. Reglas de oro de las ventas

Son la extensión de las reglas que ya tiene el proyecto. Ninguna fase se da por terminada si rompe alguna.

1. **Java calcula todo el dinero.** Subtotales, descuentos, impuestos, total, cambio y diferencias de caja se calculan en
   el backend con `BigDecimal` (2 decimales, redondeo `HALF_UP`). La pantalla solo muestra lo que devuelve el servidor.
2. **El stock solo cambia por `movimiento_inventario`.** Una venta genera `SALIDA_VENTA`; una devolución de cliente,
   `DEVOLUCION_CLIENTE`. Todo en la misma transacción que la venta: si un producto no alcanza, no se vende nada.
3. **Una venta confirmada es inmutable.** No se edita ni se borra. Para corregirla se **anula** (mismo turno, con motivo y
   autorización) o se hace una **devolución** (cualquier día, con su propio consecutivo). El consecutivo anulado no se
   reutiliza: no hay huecos en la numeración.
4. **Foto fija en cada línea.** La venta guarda el precio, el impuesto, el descuento y el costo **del momento de la
   venta**. Si mañana cambia el precio, las ventas de ayer no cambian.
5. **Todo peso que entra o sale de una caja tiene un registro.** Ventas, devoluciones en efectivo, retiros, gastos menores
   y base de apertura. La caja se cuadra con esos registros, no con lo que alguien recuerde.
6. **Aislamiento por empresa.** Toda tabla nueva lleva `empresa_id` (de `@TenantId`, nunca del request) y su prueba de
   fuga entre empresas en `AislamientoEmpresasTest`.
7. **Doble permiso.** Módulo activo para la empresa (`@RequiereModulo`) y rol del usuario (`@RequiereRol`).
8. **Acciones sensibles → `auditoria`.** Anular, devolver, dar descuento por encima del límite, cambiar precios, abrir y
   cerrar turno, registrar diferencias de caja.
9. **Nadie autoriza lo suyo.** El cajero no aprueba su propia anulación, su propio descuento especial ni su propia
   diferencia de caja (igual que en las solicitudes).

---

## 3. Glosario

| Término | Significado |
|---|---|
| **Caja** | Punto de venta físico (por ejemplo "Caja 1 mostrador"). Pertenece a una sede y descuenta de una bodega. |
| **Turno** | Periodo en que un cajero trabaja una caja: se abre con una base en efectivo y se cierra con un arqueo. |
| **Arqueo** | Conteo físico del dinero al cerrar el turno. |
| **Cierre ciego** | El cajero cuenta y escribe lo que tiene **sin ver** cuánto debería tener. Después el sistema compara. |
| **Diferencia** | Contado menos esperado. Sobrante (positivo) o faltante (negativo). Siempre queda registrada. |
| **Anulación** | Deja sin efecto una venta del turno abierto (error de digitación, cliente desistió). Devuelve stock y dinero. |
| **Devolución** | El cliente regresa productos de una venta ya cerrada. Tiene su propio consecutivo y reintegra según el estado del producto. |
| **Medio de pago** | Efectivo, tarjeta débito, tarjeta crédito, transferencia, billetera digital (Nequi, Daviplata), crédito del cliente. |
| **Cartera** | Lo que los clientes deben por ventas a crédito. |

---

## 4. Roles y permisos

Se usa el catálogo global de roles. Se agregan **dos roles** nuevos: `SUPERVISOR_VENTAS` y `VENDEDOR`.
`CAJERO` ya existe y por fin tiene pantallas.

| Acción | CAJERO | VENDEDOR | SUPERVISOR_VENTAS | ADMIN_SISTEMA (dueño) | AUDITOR |
|---|---|---|---|---|---|
| Abrir y cerrar **su** turno | ✔ | | ✔ | | |
| Vender y cobrar en su turno | ✔ | | ✔ | | |
| Armar una cotización o pedido de venta (sin cobrar) | ✔ | ✔ | ✔ | | |
| Descuento hasta el límite de su rol | ✔ | ✔ | ✔ | | |
| Autorizar descuento por encima del límite | | | ✔ | ✔ | |
| Anular una venta del turno | pide | | autoriza | autoriza | |
| Devolución de cliente | pide | | autoriza | autoriza | |
| Retiro de efectivo o gasto menor | pide | | autoriza | autoriza | |
| Aprobar la diferencia de un cierre | | | ✔ (no la suya) | ✔ | |
| Precios de venta y listas | | | | ✔ (o `ADMIN_COMPRAS` si el dueño lo delega) | lee |
| Clientes | crea y edita | crea y edita | ✔ | ✔ | lee |
| Reportes de ventas y caja | su turno | sus ventas | todos | todos | todos |

**Autorización en el mostrador.** Cuando el cajero necesita una autorización (anular, descuento alto), el supervisor la da
**en la misma pantalla** con su correo y un **PIN de supervisor de 6 dígitos** (se guarda solo el hash con BCrypt, con
bloqueo tras 5 intentos fallidos). Queda auditado quién pidió y quién autorizó. No hace falta cerrar la sesión del cajero.

---

## 5. Módulos y planes (SaaS)

| Módulo nuevo | Qué incluye |
|---|---|
| `VENTAS` | POS, clientes, precios de venta, listas de precios, impuestos, devoluciones |
| `CAJA` | Cajas, turnos, movimientos de caja, arqueo y cierre ciego |
| `CARTERA` | Ventas a crédito, cupos, abonos y edades de cartera |

- **Plan Básico:** no los trae (es solo compras e inventario).
- **Plan Profesional:** `VENTAS` + `CAJA`.
- **Plan Empresarial:** todo, incluido `CARTERA`.

Cada módulo se activa o se apaga desde la consola del dueño del software, igual que los actuales.
Con el módulo apagado la API responde `403 MODULO_NO_CONTRATADO`; con la suscripción morosa, solo lectura.

---

## 6. Modelo de datos

Todas las tablas llevan `empresa_id` con `@TenantId`. Los montos son `DECIMAL(18,2)`; las cantidades, `DECIMAL(18,4)`.
Las migraciones empiezan en **V26**. Nada se borra: los registros se anulan o se desactivan.

### 6.1 Catálogo de venta

**`tarifa_impuesto`**: IVA 19 %, IVA 5 %, exento, excluido (configurable por empresa).
`id, empresa_id, codigo, nombre, porcentaje, activo`.

**Cambios en `producto`:**
- `codigo_barras VARCHAR(50) NULL` (único por empresa cuando existe) para el lector.
- `tarifa_impuesto_id` (impuesto de venta).
- `vendible BOOLEAN` (hay productos que solo se consumen internamente).
- `precio_incluye_impuesto BOOLEAN` (en mostrador el precio suele incluir IVA; ver decisión D2).
- `costo_promedio DECIMAL(18,4)` (fase F8: lo actualiza la recepción).

**`lista_precio`**: "General" (obligatoria), "Mayorista", "Taller aliado"…
`id, empresa_id, nombre, es_general, activo`.

**`precio_producto`** (historial de precios, nunca se sobrescribe):
`id, empresa_id, lista_precio_id, producto_id, unidad_medida_id, precio, precio_minimo, vigente_desde, vigente_hasta,
creado_por_usuario_id, motivo`.
- Cambiar un precio **cierra** la vigencia del anterior y crea uno nuevo. Así se sabe qué precio tenía cada día.
- `precio_minimo` es el piso: vender por debajo exige autorización de supervisor.
- Permite precio por caja y por unidad del mismo producto (usa la conversión de unidades que ya existe).

### 6.2 Clientes

**`cliente`**: `id, empresa_id, tipo_documento (CC, NIT, CE, PAS), numero_documento, nombre, telefono, correo,
direccion, lista_precio_id, cupo_credito, plazo_dias, activo, acepta_datos_en, creado_en`.
- Único por empresa: `(tipo_documento, numero_documento)`.
- Existe siempre un cliente **"Consumidor final"** por empresa (ventas rápidas sin datos).
- `acepta_datos_en`: fecha de la autorización de tratamiento de datos (Ley 1581 de 2012, habeas data).

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

### 6.5 Cartera (fase F7)

**`cuenta_por_cobrar`**: una por venta a crédito. `venta_id, cliente_id, valor, saldo, vence_en, estado`.
**`abono_cliente`**: `cliente_id, turno_id, valor, medio_pago, aplicado_a (JSON por factura)`. Entra a la caja como `ABONO_CARTERA`.

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

### 7.8 Crédito y cartera (F7)
- Venta a crédito solo con cliente identificado y cupo disponible (cupo − saldo).
- Cada abono entra a la caja del turno, se aplica a la cuenta más antigua y deja recibo.
- Reporte de edades: al día, 1–30, 31–60, 61–90, +90 días. Cliente con mora no compra a crédito.

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

**Cuadre diario automático (job nocturno, por empresa):**
1. Suma de `venta.total` confirmadas = suma de `movimiento_caja` tipo `VENTA` del día.
2. Suma de cantidades vendidas = suma de `SALIDA_VENTA` con referencia a ventas del día.
3. Turnos abiertos por más de 24 h.
4. Cualquier descuadre genera notificación al dueño y queda en un reporte.

---

## 9. Seguridad

- **Aislamiento:** `@TenantId` en todas las tablas nuevas; pruebas de fuga entre empresas por cada endpoint (A pide un
  dato de B ⇒ 404).
- **Permisos:** `@RequiereModulo` + `@RequiereRol` en cada endpoint; las reglas de "nadie autoriza lo suyo" en el Service.
- **PIN de supervisor:** BCrypt, 6 dígitos, bloqueo tras 5 fallos (15 min), auditado, nunca viaja en la URL.
- **Datos de tarjeta:** solo últimos 4 dígitos y número de autorización (fuera del alcance de PCI DSS).
- **Datos personales de clientes:** mínimo necesario, fecha de la autorización de tratamiento de datos, exportación
  solo para roles con permiso. El consumidor final no pide datos.
- **Rate limit** en las rutas de autorización con PIN (además del que ya existe en el login).
- **Auditoría** de: venta, anulación, devolución, descuento autorizado, cambio de precio, apertura y cierre de turno,
  aprobación de diferencia, retiro, gasto, reimpresión, cambio de cupo de crédito.
- **Concurrencia:** `@Version` en `turno_caja` y `venta`; bloqueo pesimista del stock al vender.
- **Validación:** cantidades > 0, máximo de líneas por venta (ej. 300), máximo de descuento 100 %, montos con 2 decimales.

---

## 10. API (borrador)

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/ventas/productos?q=` | CAJERO, VENDEDOR, SUPERVISOR | Busca por código, código de barras o nombre; trae precio vigente y stock de la bodega de la caja |
| `GET/POST/PUT /api/precios` | ADMIN_SISTEMA | Precios por lista con historial |
| `GET/POST/PUT /api/listas-precio` | ADMIN_SISTEMA | Listas de precios |
| `GET/POST/PUT /api/impuestos` | ADMIN_SISTEMA | Tarifas de impuesto |
| `GET/POST/PUT /api/clientes` | CAJERO, VENDEDOR, SUPERVISOR | Clientes |
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
| `GET /api/cartera`, `POST /api/abonos` | CAJERO, SUPERVISOR | Cartera y abonos (F7) |
| `GET /api/reportes/ventas` | SUPERVISOR, ADMIN_SISTEMA, AUDITOR | Ventas por día, producto, cajero, medio de pago, margen |
| `GET /api/reportes/caja` | SUPERVISOR, ADMIN_SISTEMA, AUDITOR | Cierres, diferencias, retiros, anulaciones |

**Autorización de un solo uso:** el supervisor escribe su PIN y el backend devuelve un token corto (2 minutos, una sola
acción, ligado al turno y a la venta). La acción del cajero lo consume. Así el PIN no viaja en cada petición y no se
puede reutilizar.

---

## 11. Pantallas (frontend)

| Pantalla | Para quién | Lo principal |
|---|---|---|
| **POS** (`/app/pos`) | Cajero | Campo con foco permanente para el lector (Enter agrega), atajos de teclado (F2 buscar, F4 cliente, F9 cobrar, Esc cancelar), carrito con cantidades editables, totales que vienen del servidor, cobro con pagos mixtos y cálculo del cambio por el backend, aviso de tope de efectivo |
| **Tiquete** | Cajero | Formato 80 mm para impresora térmica (CSS de impresión), con consecutivo, NIT, impuestos discriminados y "COPIA" en reimpresiones |
| **Mi turno** | Cajero | Abrir, retiros y gastos, cierre ciego |
| **Clientes** | Cajero, vendedor | Búsqueda por documento, creación rápida desde el POS |
| **Precios y listas** | Dueño | Precio por lista, historial, cambio masivo por Excel (reutiliza la carga masiva) |
| **Ventas** | Supervisor, dueño | Historial con filtros, detalle, anular y devolver |
| **Cajas y turnos** | Supervisor, dueño | Turnos abiertos, cierres, diferencias por aprobar |
| **Cartera** (F7) | Supervisor, dueño | Saldos, edades, abonos |
| **Tablero de ventas** | Dueño | Ventas de hoy, del mes, ticket promedio, margen, top productos, por medio de pago, por cajero, diferencias de caja |

Reglas de siempre: la UI nunca calcula dinero ni stock; tipos generados del Swagger; pruebas Vitest por pantalla.

---

## 12. Reportes e historial

- **Ventas:** por día, semana y mes; por producto, categoría, cliente, vendedor, cajero, caja y medio de pago.
- **Margen** (F8): venta − costo (con el costo guardado en la línea), por producto y por categoría.
- **Caja:** cierres por turno, diferencias por cajero, retiros, gastos, anulaciones y descuentos autorizados.
- **Inventario:** la rotación existente ya cuenta `SALIDA_VENTA`; se le restan `ANULACION_VENTA` y `DEVOLUCION_CLIENTE`.
- **Exportación** a Excel y PDF reutilizando `exportacion/` (no consulta por su cuenta).
- **Informe de compras al dueño:** se puede ampliar con un resumen de ventas del periodo.

---

## 13. Pruebas

| Nivel | Qué cubre |
|---|---|
| **Unitarias** (JUnit + Mockito) | Cálculo de totales, impuestos (precio con y sin IVA incluido), descuentos, redondeo, cambio, esperado de caja, reglas de autorización |
| **Integración con MySQL real** (`supply_core_test`) | Venta completa en una transacción; idempotencia; rechazo por stock; anulación y devolución reintegran stock y caja |
| **Fuga entre empresas** (`AislamientoEmpresasTest`) | Una prueba por cada tabla y endpoint nuevo |
| **Concurrencia** | Dos ventas simultáneas del último producto: una pasa y la otra recibe `STOCK_INSUFICIENTE`; el stock nunca queda negativo |
| **Propiedades de dinero** | Para muchas ventas aleatorias: total = Σ líneas; Σ pagos − cambio = total; caja esperada = Σ movimientos |
| **Frontend** (Vitest) | POS, cobro, tiquete, cierre ciego (no muestra el esperado antes de guardar), autorización |
| **De punta a punta** (Playwright, fase F10) | Abrir turno → vender → anular con PIN → cerrar turno, contra el stack de Docker |

**Meta de cobertura** en los Services de ventas y caja: 90 % de líneas (medida con JaCoCo en CI).

---

## 14. Integración continua (GitHub Actions)

Se construye **primero** (fase F0), para que todo lo demás nazca probado.

**`.github/workflows/ci.yml`**: corre en cada PR hacia `develop` y `main`, y en cada push a esas ramas.

| Trabajo | Pasos |
|---|---|
| `backend` | Java 21 (Temurin) con caché de Maven → servicio MySQL 8 con `supply_core_test` → `./mvnw verify` (pruebas, incluida la de fuga entre empresas) → Javadoc con `-Ddoclint=all` → reporte JaCoCo como artefacto |
| `frontend` | Node 22 con caché de npm → `npm ci` → `npm test -- --watch=false` → `npm run build` (producción) |
| `docker` | Construye las imágenes `api` y `web` (sin publicarlas) → escaneo con Trivy (falla con vulnerabilidades CRÍTICAS) |
| `e2e` (F10) | `docker compose up` con un `.env` de prueba generado en el job → siembra la demo → Playwright |

**Seguridad del CI:**
- Sin secretos reales: las claves de prueba se generan dentro del job. Gemini y Claude no se usan en CI (`IA_PROVEEDOR=ninguno`).
- `permissions: contents: read` por defecto.
- Acciones fijadas por versión.
- **Dependency Review** en cada PR para bloquear dependencias con vulnerabilidades conocidas.
- **CodeQL** semanal para Java y TypeScript.

**Protección de ramas** (la configura el dueño del repositorio en GitHub): `develop` y `main` exigen que pasen
`backend`, `frontend` y `docker` antes de mezclar, y al menos una revisión.

---

## 15. Fases (cada una en su rama, desde `develop`)

| Fase | Rama | Entrega | Listo cuando |
|---|---|---|---|
| **F0** | `feature/ci-github-actions` | CI de backend, frontend y Docker; Dependency Review; CodeQL | Un PR de prueba muestra los 3 trabajos en verde |
| **F1** | `feature/ventas-catalogo` | Módulos `VENTAS`, `CAJA` y `CARTERA` en planes; roles nuevos; impuestos; código de barras; listas y precios con historial; pantalla de precios | Precio vigente por lista y por unidad; historial; pruebas de fuga |
| **F2** | `feature/ventas-clientes` | Clientes, consumidor final, autorización de datos | CRUD con validación de documento y fuga |
| **F3** | `feature/caja-turnos` | Cajas, turnos, movimientos de caja, PIN de supervisor y autorizaciones de un solo uso, cierre ciego, aprobación | Esperado y diferencias calculados en Java; no se muestra el esperado antes de contar |
| **F4** | `feature/ventas-pos-backend` | Venta en una transacción: precios, descuentos, impuestos, pagos mixtos, cambio, stock, caja, idempotencia | Pruebas de concurrencia, idempotencia y propiedades de dinero en verde |
| **F5** | `feature/ventas-pos-frontend` | POS con lector y atajos, cobro, tiquete 80 mm, mi turno | Venta completa desde la pantalla en Docker |
| **F6** | `feature/ventas-anulacion-devolucion` | Anulación en turno y devoluciones con estado del producto y reembolso | Stock y caja vuelven exactos; consecutivos sin huecos |
| **F7** | `feature/ventas-cartera` | Crédito, cupos, abonos, edades | Cliente en mora no compra a crédito; abonos cuadran en caja |
| **F8** | `feature/inventario-costo-promedio` | Costo promedio ponderado en la recepción; costo en la línea de venta; margen | Margen por producto coincide con un cálculo a mano |
| **F9** | `feature/ventas-reportes` | Reportes de ventas y caja, tablero del dueño, cuadre diario automático, exportaciones | El cuadre detecta un descuadre provocado en la prueba |
| **F10** | `feature/ventas-endurecimiento` | Alertas (anulaciones y descuentos por cajero), pruebas de punta a punta en CI, prueba de carga del POS | Playwright en verde en CI; 50 ventas por minuto sin errores |
| Futuro | — | Factura electrónica DIAN; repuestos: compatibilidad por marca y modelo de vehículo y referencias cruzadas | Spec propia |

Cada fase deja: código, pruebas (incluida la de fuga), Javadoc completo, tipos del frontend regenerados, `DOCKER.md` o la
skill actualizados si cambia algo de operación, y la siembra de la demo ampliada para poder probarla.

---

## 16. Decisiones que debes tomar antes de empezar

| # | Pregunta | Recomendación |
|---|---|---|
| D1 | ¿Se permite vender sin stock (stock negativo)? | **No.** Es la principal fuente de descuadres. |
| D2 | ¿El precio de mostrador incluye IVA? | **Sí**, como es costumbre en Colombia; Java separa base e impuesto. |
| D3 | ¿Redondear el total en efectivo a $50? | **Sí**, guardando la diferencia en `redondeo` para que cuadre. |
| D4 | ¿Crédito a clientes en la primera versión? | **No**: dejarlo para F7 y salir primero con contado. |
| D5 | ¿Roles nuevos `SUPERVISOR_VENTAS` y `VENDEDOR`? | **Sí**; el dueño puede asignar los dos a la misma persona en negocios pequeños. |
| D6 | ¿Tolerancia de diferencia de caja sin aprobación? | $2.000, configurable por empresa. |
| D7 | ¿Una caja puede descontar de varias bodegas? | **No**: una caja, una bodega. Los traslados ya existen. |
| D8 | ¿Quién fija precios: solo el dueño o también el jefe de compras? | El dueño, con opción de delegarlo a `ADMIN_COMPRAS`. |

---

## 17. Riesgos

| Riesgo | Cómo se mitiga |
|---|---|
| El POS debe ser muy rápido | Búsqueda indexada por código y código de barras; el carrito vive en la pantalla y solo hay una petición al cobrar. |
| Sin internet no se puede vender | Fuera de esta spec. Se documenta como limitación; un modo sin conexión sería una spec aparte. |
| Bloqueos de la base con muchas cajas | Bloqueo solo de las filas de stock de la venta, en orden de id, y transacciones cortas. |
| La DIAN cambia el esquema de facturación | El modelo de venta ya guarda base, impuesto por línea y datos del cliente; la factura será una capa encima. |
