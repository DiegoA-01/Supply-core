# Plan 008

## Datos (migración: la siguiente libre, reservarla en `agents/specs/README.md`)
- `producto.costo_promedio DECIMAL(18,4)`: lo actualiza la recepción. Se inicializa con el último costo de compra
  (`costo_referencia`) para los productos que ya existen.
- `movimiento_inventario.costo_unitario DECIMAL(18,4)`: para calcular el costo promedio ponderado en cada recepción y
  dejar el costo de cada salida.

## Lógica
- `RecepcionService` (al aceptar mercancía) calcula en Java, con la fila de stock bloqueada:
  `nuevo = (existencia × costo_promedio + cantidad × costo_compra) ÷ (existencia + cantidad)`, con la cantidad
  convertida a la unidad del producto y escala 4 `HALF_UP`. Si la existencia era 0, el nuevo costo es el de la compra.
- `MovimientoInventarioService` guarda `costo_unitario` en cada movimiento (entradas con el costo de compra; salidas,
  traslados y ajustes con el costo promedio vigente).
- La venta (004) guarda `venta_linea.costo_unitario` = costo promedio del momento.
- `CalculoMargen` (001) pasa a usar el costo promedio.
- Alerta `PRECIO_BAJO_COSTO` (aviso a ADMIN_SISTEMA) cuando una venta confirma una línea con precio neto menor que su
  costo.

## API
- Sin rutas nuevas: `ProductoResponse` y la respuesta de precios suman `costoPromedio`. Regenerar tipos.

## Pruebas
- Unitarias: costo ponderado con existencia 0, con conversión de unidades y con varias recepciones; margen.
- Integración: recepción → costo promedio actualizado → venta guarda el costo → margen igual al cálculo a mano.
- Concurrencia: dos recepciones simultáneas del mismo producto dejan el costo correcto.
- Fuga: las consultas de costo y margen no cruzan empresas.
