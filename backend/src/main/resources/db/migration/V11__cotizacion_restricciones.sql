-- Paso 12 (UC-07): una línea por producto de la RFQ en cada cotización, valores válidos
-- protegidos también en la BD, y el evento de cotización recibida.
ALTER TABLE cotizacion_linea
    ADD CONSTRAINT uq_cotizacion_linea_rfq UNIQUE (cotizacion_id, rfq_linea_id),
    ADD CONSTRAINT chk_cotlinea_cantidad  CHECK (cantidad_ofertada > 0),
    ADD CONSTRAINT chk_cotlinea_factor    CHECK (factor_conversion > 0),
    ADD CONSTRAINT chk_cotlinea_precio    CHECK (precio_unitario_ofertado >= 0),
    ADD CONSTRAINT chk_cotlinea_descuento CHECK (descuento_pct BETWEEN 0 AND 100),
    ADD CONSTRAINT chk_cotlinea_impuesto  CHECK (impuesto_pct BETWEEN 0 AND 100);

CREATE INDEX idx_cotizacion_rfq_estado ON cotizacion (rfq_id, estado);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('COTIZACION_RECIBIDA', 'INTERNA', 1),
    ('COTIZACION_RECIBIDA', 'EMAIL',   1);
