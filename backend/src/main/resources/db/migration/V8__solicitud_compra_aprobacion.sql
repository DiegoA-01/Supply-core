-- Paso 10 (UC-04/UC-05): datos que la aprobación necesita guardar en la solicitud.
-- monto_estimado     = suma de cantidad x costo_referencia del producto (2 decimales).
-- monto_incompleto   = 1 si alguna línea no tenía costo de referencia (entonces se exige aprobación).
-- politica_aprobacion_id = política asignada al enviar (se conserva aunque la política cambie después).
ALTER TABLE solicitud_compra
    ADD COLUMN monto_estimado DECIMAL(18,2) NULL AFTER fecha_requerida,
    ADD COLUMN monto_incompleto TINYINT(1) NOT NULL DEFAULT 0 AFTER monto_estimado,
    ADD COLUMN politica_aprobacion_id BIGINT NULL AFTER monto_incompleto,
    ADD COLUMN enviado_en DATETIME NULL AFTER estado,
    ADD CONSTRAINT fk_solicitud_politica FOREIGN KEY (politica_aprobacion_id) REFERENCES politica_aprobacion(id);

CREATE INDEX idx_solicitud_estado ON solicitud_compra (estado);
CREATE INDEX idx_solicitud_solicitante ON solicitud_compra (usuario_solicitante_id, estado);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('SOLICITUD_PENDIENTE_APROBACION', 'INTERNA', 1),
    ('SOLICITUD_PENDIENTE_APROBACION', 'EMAIL',   1),
    ('SOLICITUD_DECIDIDA',             'INTERNA', 1),
    ('SOLICITUD_DECIDIDA',             'EMAIL',   1),
    ('SOLICITUD_APROBADA',             'INTERNA', 1),
    ('SOLICITUD_APROBADA',             'EMAIL',   1);
