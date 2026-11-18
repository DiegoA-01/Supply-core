-- Paso 13 (UC-08): lectura de cotizaciones con IA y análisis comparativo.
-- Un procesamiento de IA por cotización (si se reintenta, se reutiliza la fila).
ALTER TABLE procesamiento_ia
    ADD CONSTRAINT uq_procesamiento_cotizacion UNIQUE (cotizacion_id);
CREATE INDEX idx_procesamiento_estado ON procesamiento_ia (estado);

-- Explicación del ranking completo redactada por la IA (opcional); la de cada cotización la arma Java.
ALTER TABLE analisis_cotizacion ADD COLUMN explicacion_general TEXT NULL AFTER estado;
CREATE INDEX idx_analisis_rfq_estado ON analisis_cotizacion (rfq_id, estado);

ALTER TABLE extraccion_campo
    ADD CONSTRAINT chk_extraccion_confianza CHECK (confianza BETWEEN 0 AND 1);

-- Aviso al comprador cuando la IA no pudo leer bien una cotización (o falló).
INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('EXTRACCION_REQUIERE_REVISION', 'INTERNA', 1),
    ('EXTRACCION_REQUIERE_REVISION', 'EMAIL',   1);
