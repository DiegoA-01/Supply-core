# Plan 005

## Backend
- `GET /api/ventas/{id}/tiquete` (según rol): datos del tiquete; la segunda vez y siguientes marca **COPIA** y audita
  `VENTA_REIMPRIMIR`.
- `POST /api/ventas/cotizar` (CAJERO, VENDEDOR, SUPERVISOR_VENTAS): recibe el carrito y devuelve los totales y el cambio
  calculados en Java **sin guardar nada**, para que la pantalla los muestre mientras se arma la venta. Usa el mismo
  cálculo de la 004.

## Frontend (zona `/app`, módulos VENTAS y CAJA)

| Pantalla | Para quién | Lo principal |
|---|---|---|
| **POS** (`/app/pos`) | Cajero | Campo con foco permanente para el lector (Enter agrega), atajos de teclado (F2 buscar, F4 cliente, F9 cobrar, Esc cancelar), carrito con cantidades editables, totales que vienen del servidor, cobro con pagos mixtos y cálculo del cambio por el backend, aviso de tope de efectivo |
| **Tiquete** | Cajero | Formato 80 mm para impresora térmica (CSS de impresión `@page { size: 80mm auto }`), con consecutivo, NIT, impuestos discriminados y "COPIA" en reimpresiones |
| **Mi turno** (`/app/caja/mi-turno`) | Cajero | Abrir, retiros y gastos, cierre ciego |
| **Ventas** (`/app/ventas`) | Supervisor, dueño | Historial con filtros y detalle (anular y devolver llegan en la 006) |

- El carrito vive en un signal del componente; la clave de idempotencia se genera con `crypto.randomUUID()` al abrir el
  carrito y se reenvía igual si el cobro falla por red.
- Reutiliza: búsqueda de productos (001), creación rápida de cliente (002), diálogo de PIN (003).
- Sin turno abierto, el POS lleva a "Mi turno".
- Reglas de siempre: la UI nunca calcula dinero ni stock; tipos generados del Swagger; pruebas Vitest por pantalla.

## Pruebas
- Vitest: Enter en el lector agrega; atajos; totales mostrados son los del servidor; reenvío con la misma clave;
  pagos mixtos; tiquete con "COPIA"; cierre ciego sin esperado antes de guardar; autorización con PIN.
- Backend: `cotizar` no guarda nada y coincide con la venta confirmada; la reimpresión marca COPIA y se audita; fuga del
  tiquete.
- Manual en Docker: abrir turno → vender con lector y pagos mixtos → imprimir → cerrar turno.
