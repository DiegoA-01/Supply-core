-- Paso 11 (UC-06): fechas de apertura y cierre de la RFQ, índices para el job de cierre
-- y la bandeja del proveedor, y eventos de notificación de RFQ.
ALTER TABLE rfq
    ADD COLUMN abierta_en DATETIME NULL AFTER estado,
    ADD COLUMN cerrada_en DATETIME NULL AFTER abierta_en;

CREATE INDEX idx_rfq_estado_limite ON rfq (estado, fecha_limite);
CREATE INDEX idx_rfq_proveedor_proveedor ON rfq_proveedor (proveedor_id);

INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('RFQ_INVITACION', 'INTERNA', 1),
    ('RFQ_INVITACION', 'EMAIL',   1),
    ('RFQ_CERRADA',    'INTERNA', 1),
    ('RFQ_CERRADA',    'EMAIL',   1),
    ('RFQ_CANCELADA',  'INTERNA', 1),
    ('RFQ_CANCELADA',  'EMAIL',   1);
