-- Spec agents/specs/008-costo-promedio-margen: costo promedio ponderado del producto y costo de cada movimiento.

-- Lo actualiza la recepción: (existencia × costo anterior + cantidad × costo de compra) ÷ (existencia + cantidad).
-- Los productos que ya existen arrancan con su último costo de compra.
ALTER TABLE producto ADD COLUMN costo_promedio DECIMAL(18,4) NULL;
UPDATE producto SET costo_promedio = costo_referencia WHERE costo_referencia IS NOT NULL;

-- Costo unitario con que se movió cada unidad: en las compras, el de la compra; en lo demás, el promedio vigente.
-- Los movimientos anteriores quedan sin costo (no se recalcula el pasado).
ALTER TABLE movimiento_inventario ADD COLUMN costo_unitario DECIMAL(18,4) NULL;
