-- Un TRASLADO afecta dos bodegas en un solo movimiento:
--   stock_resultante         = cómo quedó la bodega de ORIGEN
--   stock_resultante_destino = cómo quedó la bodega de DESTINO (solo en traslados)
-- Así el kardex de cada bodega muestra su saldo después de cada movimiento.

ALTER TABLE movimiento_inventario
    ADD COLUMN stock_resultante_destino DECIMAL(18,4) NULL AFTER stock_resultante;

-- Consultas del kardex por bodega (origen o destino) ordenadas por fecha
CREATE INDEX idx_mov_bodega_origen  ON movimiento_inventario (bodega_origen_id, creado_en);
CREATE INDEX idx_mov_bodega_destino ON movimiento_inventario (bodega_destino_id, creado_en);
