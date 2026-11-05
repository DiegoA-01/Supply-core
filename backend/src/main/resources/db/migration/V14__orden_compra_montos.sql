-- Paso 15 (UC-10/UC-12): montos de la orden (subtotal + impuestos = total), quién la emitió,
-- impuesto por línea, índices y eventos.
ALTER TABLE orden_compra
    ADD COLUMN subtotal  DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER motivo_version,
    ADD COLUMN impuestos DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER subtotal,
    ADD COLUMN usuario_id BIGINT NULL AFTER bodega_destino_id,
    ADD CONSTRAINT fk_orden_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id);

-- cantidad y precio_unitario van en la unidad del producto (la que usa el inventario)
ALTER TABLE orden_compra_linea
    ADD COLUMN impuesto_pct DECIMAL(5,2) NOT NULL DEFAULT 0 AFTER precio_unitario,
    ADD CONSTRAINT chk_ocl_impuesto CHECK (impuesto_pct BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_ocl_precio CHECK (precio_unitario >= 0);

CREATE INDEX idx_orden_proveedor_estado ON orden_compra (proveedor_id, estado);
CREATE INDEX idx_orden_estado ON orden_compra (estado);
CREATE INDEX idx_orden_historial ON orden_compra_estado_historial (orden_compra_id, creado_en);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('ORDEN_EMITIDA',             'INTERNA', 1),
    ('ORDEN_EMITIDA',             'EMAIL',   1),
    ('ORDEN_ACTUALIZADA',         'INTERNA', 1),
    ('ORDEN_ACTUALIZADA',         'EMAIL',   1),
    ('ORDEN_RESPUESTA_PROVEEDOR', 'INTERNA', 1),
    ('ORDEN_RESPUESTA_PROVEEDOR', 'EMAIL',   1),
    ('ORDEN_DESPACHADA',          'INTERNA', 1),
    ('ORDEN_DESPACHADA',          'EMAIL',   1);
