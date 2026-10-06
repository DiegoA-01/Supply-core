-- Paso 14 (UC-09): política que exigió la aprobación (se conserva aunque cambie), índices y eventos.
ALTER TABLE adjudicacion
    ADD COLUMN politica_aprobacion_id BIGINT NULL AFTER estado,
    ADD CONSTRAINT fk_adjudicacion_politica FOREIGN KEY (politica_aprobacion_id) REFERENCES politica_aprobacion(id);

CREATE INDEX idx_adjudicacion_rfq_estado ON adjudicacion (rfq_id, estado);
CREATE INDEX idx_adjudicacion_estado ON adjudicacion (estado);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('ADJUDICACION_PENDIENTE_APROBACION', 'INTERNA', 1),
    ('ADJUDICACION_PENDIENTE_APROBACION', 'EMAIL',   1),
    ('ADJUDICACION_DECIDIDA',             'INTERNA', 1),
    ('ADJUDICACION_DECIDIDA',             'EMAIL',   1),
    ('COTIZACION_SELECCIONADA',           'INTERNA', 1),
    ('COTIZACION_SELECCIONADA',           'EMAIL',   1);
