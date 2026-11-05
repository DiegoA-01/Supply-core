-- Paso 16 (UC-11): registro de pagos de una orden. Sin pasarela ni contabilidad.
-- Un pago registrado por error no se borra: se ANULA con motivo y deja de sumar.
ALTER TABLE pago
    MODIFY estado ENUM('PENDIENTE','REGISTRADO','ANULADO') NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN motivo_anulacion       VARCHAR(500) NULL AFTER comprobante_documento_id,
    ADD COLUMN anulado_por_usuario_id BIGINT       NULL AFTER motivo_anulacion,
    ADD COLUMN anulado_en             DATETIME     NULL AFTER anulado_por_usuario_id,
    ADD CONSTRAINT fk_pago_anulado_por FOREIGN KEY (anulado_por_usuario_id) REFERENCES usuario(id),
    ADD CONSTRAINT chk_pago_anulacion CHECK (estado <> 'ANULADO' OR motivo_anulacion IS NOT NULL);

CREATE INDEX idx_pago_orden_estado ON pago (orden_compra_id, estado);
CREATE INDEX idx_pago_fecha ON pago (fecha_pago);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('PAGO_REGISTRADO', 'INTERNA', 1),
    ('PAGO_REGISTRADO', 'EMAIL',   1),
    ('PAGO_ANULADO',    'INTERNA', 1),
    ('PAGO_ANULADO',    'EMAIL',   1);
