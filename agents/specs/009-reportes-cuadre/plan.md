# Plan 009

## Datos (migración solo si hace falta, reservarla en `agents/specs/README.md`)
- `cuadre_diario`: `id, empresa_id, fecha, total_ventas, total_caja_ventas, unidades_vendidas, unidades_salida,
  turnos_abiertos_24h, cuadra, detalle (JSON), creado_en`. Foto fija del resultado de cada noche.
- Evento `CUADRE_DESCUADRE` en `EventoNotificacion` (sembrar sus canales por empresa).

## Reportes (paquete `reporte/`, solo lectura)
- **Ventas:** por día, semana y mes; por producto, categoría, cliente, vendedor, cajero, caja y medio de pago.
- **Margen:** venta − costo (con el costo guardado en la línea), por producto y por categoría.
- **Caja:** cierres por turno, diferencias por cajero, retiros, gastos, anulaciones y descuentos autorizados.
- **Inventario:** la rotación existente ya cuenta `SALIDA_VENTA` y resta `ANULACION_VENTA` y `DEVOLUCION_CLIENTE` (006).
- **Exportación** a Excel y PDF reutilizando `exportacion/` (no consulta por su cuenta).
- **Informe de compras al dueño:** se puede ampliar con un resumen de ventas del periodo (`InformeContenido`).

## Cuadre diario automático (job nocturno, por empresa con `EmpresaContexto.como`)
1. Suma de `venta.total` confirmadas = suma de `movimiento_caja` tipo `VENTA` del día.
2. Suma de cantidades vendidas = suma de `SALIDA_VENTA` con referencia a ventas del día.
3. Turnos abiertos por más de 24 h.
4. Cualquier descuadre genera notificación al dueño y queda en un reporte.

## API

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/reportes/ventas` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR (cajero su turno, vendedor sus ventas) | Ventas por día, producto, cajero, medio de pago, margen |
| `GET /api/reportes/caja` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Cierres, diferencias, retiros, anulaciones |
| `GET /api/reportes/cuadre` | ADMIN_SISTEMA, AUDITOR | Resultados del cuadre diario |
| `GET /api/reportes/tablero` | (ya existe en `feature/mejoras-negocio`) | Se amplía con el bloque de ventas si el módulo VENTAS está activo |

## Frontend
- **Tablero de ventas:** dentro de `/app/tablero` (ya existe): ventas de hoy, del mes, ticket promedio, margen, top
  productos, por medio de pago, por cajero, diferencias de caja.
- **Reportes de ventas y caja** en `/app/reportes` con filtros y exportación.
- **Cuadre diario:** lista con los días que no cuadran.

## Pruebas
- Unitarias: agregaciones y margen.
- Integración: el cuadre detecta un descuadre provocado (un movimiento de caja borrado de la prueba o una salida de más).
- Propiedades: Σ por medio de pago = Σ ventas del día.
- Fuga: reportes, cuadre y tablero de A no incluyen datos de B.
- Frontend: el tablero oculta el bloque de ventas si el módulo está apagado.
