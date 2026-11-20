# Plan 009

Decisiones tomadas el 2026-11-20 al aprobar la spec:
- **Ventas netas:** cuenta lo confirmado menos lo devuelto; las ventas anuladas no cuentan. Cada reporte muestra
  también la columna bruta y la de devoluciones.
- **Cuadre a la 01:00** del día anterior completo (`app.ventas.cuadre-cron`, `"-"` lo apaga).
- **Exportación en Excel y PDF** (PDF de tabla con OpenPDF, que ya está en el proyecto).
- **Resumen de ventas en el informe de compras** solo si la empresa tiene el módulo `VENTAS`.

## Datos (V38, reservada en `agents/specs/README.md`)
- `cuadre_diario`: `id, empresa_id, fecha, ventas_confirmadas, ventas_en_caja, unidades_vendidas, unidades_salida,
  turnos_abiertos_24h, cuadra, detalle (JSON), revisado_en`. Único por `(empresa_id, fecha)`: volver a revisar un día
  reemplaza su fila. Foto fija del resultado.
- Evento `CUADRE_DESCUADRE` en `EventoNotificacion` (sin sembrar: una configuración ausente cuenta como activa).

## Reglas de cálculo (todo en Java, `BigDecimal` con 2 decimales)
- Una venta cuenta el día de `confirmada_en`; una devolución, el día de `creado_en` y con los datos de la venta
  original (producto, categoría, cliente, vendedor, cajero, caja).
- Por línea: venta = `total_linea`; base sin IVA = `base_gravable`; costo = `costo_unitario × cantidad_unidad_producto`
  (el costo guardado en la línea). La línea devuelta resta su `valor` y la parte proporcional de base y costo
  (`cantidad devuelta / cantidad vendida`).
- Margen = base neta − costo neto; margen % = margen / base neta (sin dato si la base es 0 o falta el costo).
- Por medio de pago: lo cobrado por `venta_pago.monto`; las devoluciones restan por su `medio_reembolso` (lo que fue a
  deuda o a saldo a favor va como «Crédito / saldo»).
- Ticket promedio = ventas netas / número de ventas confirmadas del periodo.

## Reportes (paquete `reporte/`, solo lectura)
- `VentaReporteService`: filas de líneas vendidas y devueltas del periodo, agrupadas en Java por la dimensión pedida
  (`DIA`, `SEMANA`, `MES`, `PRODUCTO`, `CATEGORIA`, `CLIENTE`, `VENDEDOR`, `CAJERO`, `CAJA`, `MEDIO_PAGO`), con totales
  que suman lo mismo en cualquier dimensión. Cajero: solo sus ventas; vendedor: solo las suyas.
- `CajaReporteService`: turnos del periodo (esperado, contado, diferencia, aprobación), diferencias por cajero, retiros,
  gastos, anulaciones y descuentos autorizados (líneas con `autorizado_por_usuario_id`).
- `TableroVentasService`: bloque de ventas del tablero (hoy, mes, ticket promedio, margen, 5 productos más vendidos,
  por medio de pago, por cajero, diferencias de caja del mes y turnos por aprobar).
- Exportación: `ExportacionService.ventas` y `.caja` en Excel y PDF (`TablaPdfGenerador`) con los mismos servicios.
- Informe de compras: `InformeContenido.ventas` (total neto, margen, ticket promedio y 5 productos del periodo) cuando
  el módulo `VENTAS` está activo.

## Cuadre diario (`CuadreDiarioService` + `CuadreDiarioJob`, por empresa con `EmpresaContexto.como`)
1. Lo cobrado en caja por las ventas confirmadas el día (`venta_pago` de medios que entran a caja) = movimientos de caja
   `VENTA` de esas ventas.
2. Unidades vendidas (`cantidad_unidad_producto`) = salidas `SALIDA_VENTA` con referencia a esas ventas.
3. Turnos abiertos por más de 24 horas.
4. Si algo no cuadra: fila con `cuadra = false`, detalle de cada venta o turno, y aviso `CUADRE_DESCUADRE` al dueño.

## API

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/reportes/ventas?desde&hasta&agrupar` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR, CAJERO (lo suyo), VENDEDOR (lo suyo) | Ventas netas por dimensión con margen |
| `GET /api/reportes/caja?desde&hasta` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Turnos, diferencias, retiros, gastos, anulaciones, descuentos |
| `GET /api/reportes/cuadre?soloDescuadres` | ADMIN_SISTEMA, AUDITOR | Resultados del cuadre diario |
| `POST /api/reportes/cuadre/{fecha}` | ADMIN_SISTEMA | Vuelve a revisar un día (después de corregir) |
| `GET /api/reportes/tablero/ventas` | ADMIN_SISTEMA, AUDITOR | Bloque de ventas del tablero del dueño |
| `GET /api/exportaciones/reportes/ventas` y `/caja` | los mismos de cada reporte | Excel o PDF |

Todo bajo `@RequiereModulo(VENTAS)`. El tablero de compras (`/api/reportes/tablero`) no cambia: el bloque de ventas
tiene su propia ruta para no exigir el módulo de ventas a quien solo compra, y se dibuja en la misma pantalla.

## Frontend
- **Tablero** (`/app/tablero`): bloque de ventas solo si el módulo `VENTAS` está activo y el rol lo ve.
- **Reportes de ventas** (`/app/ventas/reportes`): pestañas Ventas, Caja y Cuadre, filtros, exportación.

## Pruebas
- Unitarias: agrupación, devoluciones que restan base y costo proporcionales, margen, semana y mes.
- Propiedades: la suma por cualquier dimensión es la misma; Σ por medio de pago = Σ ventas netas.
- Integración: el cuadre detecta un movimiento de caja borrado, una salida de inventario de más y un turno de 25 horas;
  avisa al dueño; volver a revisar después de corregir deja el día cuadrado.
- Fuga: reportes, tablero, cuadre y exportaciones de A no incluyen datos de B.
- Frontend: el tablero oculta el bloque si el módulo está apagado; la pantalla nunca calcula dinero.
