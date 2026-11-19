# Spec de Ventas y Caja · Parte 3: cartera, costos, reportes y endurecimiento (F7 a F10)

**Estado:** propuesta para revisar. No hay código todavía.
**Parte de:** [SPEC_VENTAS.md](SPEC_VENTAS.md) (índice). Necesita las partes 1 y 2 terminadas (se apoya en las ventas, la caja y los turnos).
**Fecha:** 2026-11-18.

---

> Aplican sin cambios las **reglas de oro**, el **glosario**, los **roles**, la **seguridad** y la **estrategia de
> pruebas** de la [parte 1](SPEC_VENTAS_1_BASE_Y_CATALOGO.md).

## 6. Modelo de datos (parte 3)

### 6.5 Cartera (fase F7)

**`cuenta_por_cobrar`**: una por venta a crédito. `venta_id, cliente_id, valor, saldo, vence_en, estado`.
**`abono_cliente`**: `cliente_id, turno_id, valor, medio_pago, aplicado_a (JSON por factura)`. Entra a la caja como `ABONO_CARTERA`.

### Costo promedio (fase F8)

Viene de las secciones 6.1 y 6.6 de la parte 1 y 2:

- `costo_promedio DECIMAL(18,4)` (fase F8: lo actualiza la recepción).
- En `movimiento_inventario` se agrega `costo_unitario` para calcular el costo promedio ponderado en cada recepción.

---

## 7. Flujos paso a paso

### 7.8 Crédito y cartera (F7)
- Venta a crédito solo con cliente identificado y cupo disponible (cupo − saldo).
- Cada abono entra a la caja del turno, se aplica a la cuenta más antigua y deja recibo.
- Reporte de edades: al día, 1–30, 31–60, 61–90, +90 días. Cliente con mora no compra a crédito.

---

## 8. Cuadre diario automático (F9)

| Riesgo | Control |
|---|---|
| Vender por debajo del costo | `precio_minimo` y alerta si el precio queda bajo el costo promedio (F8). |
| Ventas "por fuera del sistema" | Cuadre diario automático: ventas ↔ movimientos de inventario ↔ movimientos de caja. Cualquier diferencia genera alerta. |

**Cuadre diario automático (job nocturno, por empresa):**
1. Suma de `venta.total` confirmadas = suma de `movimiento_caja` tipo `VENTA` del día.
2. Suma de cantidades vendidas = suma de `SALIDA_VENTA` con referencia a ventas del día.
3. Turnos abiertos por más de 24 h.
4. Cualquier descuadre genera notificación al dueño y queda en un reporte.

---

## 10. API de esta parte


| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/cartera`, `POST /api/abonos` | CAJERO, SUPERVISOR | Cartera y abonos (F7) |
| `GET /api/reportes/ventas` | SUPERVISOR, ADMIN_SISTEMA, AUDITOR | Ventas por día, producto, cajero, medio de pago, margen |
| `GET /api/reportes/caja` | SUPERVISOR, ADMIN_SISTEMA, AUDITOR | Cierres, diferencias, retiros, anulaciones |

**Autorización de un solo uso:** el supervisor escribe su PIN y el backend devuelve un token corto (2 minutos, una sola
acción, ligado al turno y a la venta). La acción del cajero lo consume. Así el PIN no viaja en cada petición y no se
puede reutilizar.

---

## 11. Pantallas de esta parte


| Pantalla | Para quién | Lo principal |
|---|---|---|
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

## 13. Pruebas que exige esta parte


| Nivel | Qué cubre |
|---|---|
| **Fuga entre empresas** (`AislamientoEmpresasTest`) | Una prueba por cada tabla y endpoint nuevo |
| **Propiedades de dinero** | Para muchas ventas aleatorias: total = Σ líneas; Σ pagos − cambio = total; caja esperada = Σ movimientos |
| **De punta a punta** (Playwright, fase F10) | Abrir turno → vender → anular con PIN → cerrar turno, contra el stack de Docker |

**Meta de cobertura** en los Services de ventas y caja: 90 % de líneas (medida con JaCoCo en CI).
- El trabajo `e2e` del CI (definido en la parte 1, sección 14) se activa en esta parte (F10).

---

## 15. Fases de esta parte


| Fase | Rama | Entrega | Listo cuando |
|---|---|---|---|
| **F7** | `feature/ventas-cartera` | Crédito, cupos, abonos, edades | Cliente en mora no compra a crédito; abonos cuadran en caja |
| **F8** | `feature/inventario-costo-promedio` | Costo promedio ponderado en la recepción; costo en la línea de venta; margen | Margen por producto coincide con un cálculo a mano |
| **F9** | `feature/ventas-reportes` | Reportes de ventas y caja, tablero del dueño, cuadre diario automático, exportaciones | El cuadre detecta un descuadre provocado en la prueba |
| **F10** | `feature/ventas-endurecimiento` | Alertas (anulaciones y descuentos por cajero), pruebas de punta a punta en CI, prueba de carga del POS | Playwright en verde en CI; 50 ventas por minuto sin errores |
| Futuro | — | Factura electrónica DIAN; repuestos: compatibilidad por marca y modelo de vehículo y referencias cruzadas | Spec propia |

Cada fase deja: código, pruebas (incluida la de fuga), Javadoc completo, tipos del frontend regenerados, `DOCKER.md` o la
skill actualizados si cambia algo de operación, y la siembra de la demo ampliada para poder probarla.

---

## 16. Decisiones de esta parte


| # | Pregunta | Recomendación |
|---|---|---|
| D4 | ¿Crédito a clientes en la primera versión? | **No**: dejarlo para F7 y salir primero con contado. |

---

## 17. Riesgos de esta parte


| Riesgo | Cómo se mitiga |
|---|---|
| La DIAN cambia el esquema de facturación | El modelo de venta ya guarda base, impuesto por línea y datos del cliente; la factura será una capa encima. |
