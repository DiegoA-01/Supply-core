# Specs

Una carpeta por cambio, numerada para conservar el orden: `NNN-nombre/` con `spec.md` (qué y por qué, sin tecnología),
`plan.md` (cómo: tablas, endpoints, pantallas y pruebas) y `tasks.md` (pasos T1, T2… que se marcan con `[x]` solo si sus
pruebas pasan). El flujo está en `AGENTS.md`.

Estados de una spec: **Propuesta** (en revisión) → **Aprobada** (se puede planear y construir) → **En construcción** →
**Hecha** (criterios de aceptación verificados).

> **Revisión del 2026-11-20 (rama `fix/ventas-pos-revision`, sobre `feature/ventas-pos`).** Las specs 004 a 008 se
> construyeron juntas en `feature/ventas-pos`. La revisión corrigió: la devolución de una venta a crédito rebaja primero
> su deuda (no saca efectivo ni crea saldo a favor mientras se deba); la venta devuelta del todo devuelve también el
> redondeo; el reintento con la misma clave ve la venta ya creada y rechaza otro carrito (`CLAVE_REPETIDA`); el turno se
> bloquea en la primera lectura (venta, devolución y abono, mismo orden de bloqueo); el permiso de descuento no pasa el
> límite de quien lo da; el pedido se cobra con sus mismos productos (`PEDIDO_DISTINTO`); una venta de menos de $25 no se
> redondea a cero; el costo promedio parte del costo vigente y no cuenta la bodega de averías; el abono con tarjeta o
> transferencia pide referencia; la cartera entrega `vencido`; y el POS fija el carrito mientras la venta va en camino,
> no vacía el carrito con Esc desde un campo ni sin preguntar, F9 en el cobro confirma y descarta cotizaciones viejas.

## Índice

| Spec | Fase de la spec original | Qué entrega | Rama | Depende de | Estado |
|---|---|---|---|---|---|
| [000-ci-github-actions](000-ci-github-actions/spec.md) | F0 | CI con GitHub Actions, Dependency Review y CodeQL | `feature/ci-github-actions` | — | Hecha (PR #26) |
| [001-ventas-catalogo](001-ventas-catalogo/spec.md) | F1 | Módulos, roles, impuestos, código de barras, listas y precios con historial | `feature/ventas-catalogo` | 000 | Hecha (V29 + V31, PR #28) |
| [002-ventas-clientes](002-ventas-clientes/spec.md) | F2 | Clientes, consumidor final, autorización de datos | `feature/ventas-clientes` | 001 | Hecha (V32, PR #29) |
| [003-caja-turnos](003-caja-turnos/spec.md) | F3 | Cajas, turnos, libro de caja, PIN de supervisor, cierre ciego | `feature/caja-turnos` | 001 | Hecha (V33, PR #30) |
| [004-venta-backend](004-venta-backend/spec.md) | F4 | La venta en una sola transacción: precios, descuentos, impuestos, pagos, stock, caja, idempotencia | `feature/ventas-pos` | 002, 003 | Hecha (V34, PR #32) |
| [005-pos-frontend](005-pos-frontend/spec.md) | F5 | POS con lector y atajos, cobro, tiquete de 80 mm, mi turno | `feature/ventas-pos` | 004 | Hecha (PR #32; falta prueba en Docker) |
| [006-anulacion-devolucion](006-anulacion-devolucion/spec.md) | F6 | Anulación en el turno y devolución de cliente | `feature/ventas-pos` | 004 | Hecha (V35, PR #32) |
| [007-cartera](007-cartera/spec.md) | F7 | Crédito, cupos, abonos y edades | `feature/ventas-pos` | 004 | Hecha (V37, PR #32) |
| [008-costo-promedio-margen](008-costo-promedio-margen/spec.md) | F8 | Costo promedio ponderado y margen real | `feature/ventas-pos` | 004 | Hecha (V36, PR #32) |
| [009-reportes-cuadre](009-reportes-cuadre/spec.md) | F9 | Reportes de ventas y caja, tablero, cuadre diario automático | `feature/ventas-reportes` | 006, 008 | Hecha (V38) |
| [010-endurecimiento-e2e](010-endurecimiento-e2e/spec.md) | F10 | Alertas, pruebas de punta a punta en el CI, prueba de carga | `feature/ventas-endurecimiento` | 009 | Hecha (V39; falta ver el `e2e` del CI en GitHub) |

Al terminar la 006 el negocio ya puede **vender y cuadrar caja**. De la 007 en adelante se suman crédito, márgenes y
control fino.

**Futuro (sin spec todavía):** factura electrónica DIAN; repuestos: compatibilidad por marca y modelo de vehículo y
referencias cruzadas; catálogos de proveedor en PDF leídos con IA; correo real por SMTP.

## Decisiones

| # | Pregunta | Decisión | Estado | Spec |
|---|---|---|---|---|
| D1 | ¿Se permite vender sin stock (stock negativo)? | **No.** Es la principal fuente de descuadres. | **Tomada** (2026-11-20, construida en V34) | 004 |
| D2 | ¿El precio de mostrador incluye IVA? | **Sí**, como es costumbre en Colombia; Java separa base e impuesto. | **Tomada** (construida en V29) | 001 |
| D3 | ¿Redondear el total en efectivo a $50? | **Sí**, guardando la diferencia en `redondeo` para que cuadre. | **Tomada** (2026-11-20, construida en V34) | 004 |
| D4 | ¿Crédito a clientes en la primera versión? | **Sí, con la 007**: clientes registrados, sin pasar el cupo, bloqueo por mora con excepción manual. | **Tomada** (2026-11-20) | 007 |
| D5 | ¿Roles nuevos `SUPERVISOR_VENTAS` y `VENDEDOR`? | **Sí**; el dueño puede asignar los dos a la misma persona en negocios pequeños. | **Tomada** (2026-11-19) | 001 |
| D6 | ¿Tolerancia de diferencia de caja sin aprobación? | $2.000, configurable por empresa. | **Tomada** (2026-11-19) | 003 |
| D7 | ¿Una caja puede descontar de varias bodegas? | **No**: una caja, una bodega. Los traslados ya existen. | **Tomada** (2026-11-19) | 003 |
| D8 | ¿Quién fija precios: solo el dueño o también el jefe de compras? | El dueño, con opción de delegarlo a `ADMIN_COMPRAS`. | **Tomada** (construida en V29) | 001 |
| D9 | ¿Precio distinto por caja y por unidad del mismo producto? | **Sí**, como pedía la spec original; hoy el precio se guarda solo en la unidad del producto. | **Tomada** (2026-11-19) | 001 |

Una decisión **Recomendada** pasa a **Tomada** cuando la aprueba el equipo; se anota aquí antes de aprobar la spec.

## Reserva de migraciones
Antes de crear una migración, la rama la anota aquí (en su primer commit) para que dos ramas no usen el mismo número.

| Versión | Rama | Qué |
|---|---|---|
| V25 | (en `develop`) | Informe de compras |
| V26 a V30 | (en `develop`, PR #27) | Reabastecimiento, inventario por sede, pedidos internos, precios de venta (V29), catálogos de proveedores |
| V31 | `feature/ventas-catalogo` | Completar la spec 001: módulos, roles y precio vigente único |
| V32 | `feature/ventas-clientes` | Clientes y consumidor final (spec 002) |
| V33 | `feature/caja-turnos` | Cajas, turnos, libro de caja, PIN y autorizaciones (spec 003) |
| V34 | `feature/ventas-pos` | La venta: venta, líneas, pagos, pedido de venta, límite de descuento por rol y consecutivo por caja (spec 004) |
| V35 | `feature/ventas-pos` | Anulación y devolución: tipos de movimiento, devoluciones, saldo a favor y bodega de averías (spec 006) |
| V36 | `feature/ventas-pos` | Costo promedio del producto y costo de cada movimiento (spec 008) |
| V37 | `feature/ventas-pos` | Cartera: cuentas por cobrar, abonos, saldo a favor como medio de pago y excepción de crédito (spec 007) |
| V38 | `feature/ventas-reportes` | Cuadre diario automático: `cuadre_diario` (spec 009) |
| V39 | `feature/ventas-endurecimiento` | Umbrales de anulaciones y descuentos por cajero y registro de alertas (spec 010) |
| V40 en adelante | libre | Se reserva al empezar cada spec |

## Riesgos generales

| Riesgo | Cómo se mitiga | Spec |
|---|---|---|
| El POS debe ser muy rápido | Búsqueda indexada por código y código de barras; el carrito vive en la pantalla y solo hay una petición al cobrar. | 004, 005 |
| Sin internet no se puede vender | Fuera de estas specs. Se documenta como limitación; un modo sin conexión sería una spec aparte. | — |
| Bloqueos de la base con muchas cajas | Bloqueo solo de las filas de stock de la venta, en orden de id, y transacciones cortas. | 004 |
| La DIAN cambia el esquema de facturación | El modelo de venta ya guarda base, impuesto por línea y datos del cliente; la factura será una capa encima. | futuro |
| Dos ramas construyen lo mismo o usan la misma migración | Una spec, una rama y una persona responsable; migración reservada en esta página. | todas |
