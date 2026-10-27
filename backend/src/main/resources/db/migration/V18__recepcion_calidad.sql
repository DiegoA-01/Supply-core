-- Paso 17 (UC-13): recepción de mercancía con calidad.
-- Por línea se guarda lo recibido, lo rechazado (con motivo), lo aceptado (lo único que sube el stock),
-- el excedente (llegó más de lo pedido: queda en revisión y no suma stock) y lo que aún falta.

-- La recepción pertenece a una empresa (tenant)
ALTER TABLE recepcion ADD COLUMN empresa_id BIGINT NULL;
UPDATE recepcion r JOIN orden_compra o ON o.id = r.orden_compra_id SET r.empresa_id = o.empresa_id;
ALTER TABLE recepcion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_recepcion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_recepcion_empresa (empresa_id),
    ADD INDEX idx_recepcion_orden (orden_compra_id, recibido_en);

ALTER TABLE recepcion_linea
    MODIFY estado ENUM('ACEPTADA','EN_REVISION','EXCEDENTE_DEVUELTO') NOT NULL DEFAULT 'ACEPTADA',
    ADD COLUMN cantidad_aceptada    DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER cantidad_rechazada,
    ADD COLUMN cantidad_excedente   DECIMAL(18,4) NOT NULL DEFAULT 0 AFTER cantidad_aceptada,
    ADD COLUMN motivo_rechazo       ENUM('DANADO','NO_CUMPLE_ESPECIFICACION','VENCIDO','CANTIDAD_ERRADA','OTRO') NULL AFTER estado,
    ADD COLUMN resuelto_por_usuario_id BIGINT NULL,
    ADD COLUMN resuelto_en          DATETIME      NULL,
    ADD COLUMN motivo_resolucion    VARCHAR(500)  NULL,
    ADD CONSTRAINT fk_recepcion_linea_resuelto FOREIGN KEY (resuelto_por_usuario_id) REFERENCES usuario(id),
    ADD CONSTRAINT chk_recepcion_cantidades CHECK (cantidad_recibida >= 0 AND cantidad_rechazada >= 0
        AND cantidad_rechazada <= cantidad_recibida AND cantidad_aceptada >= 0 AND cantidad_excedente >= 0
        AND cantidad_faltante >= 0),
    ADD CONSTRAINT chk_recepcion_motivo CHECK (cantidad_rechazada = 0 OR motivo_rechazo IS NOT NULL),
    ADD INDEX idx_recepcion_linea_orden_linea (orden_compra_linea_id);

-- Eventos nuevos de notificación, sembrados para cada empresa existente
INSERT INTO config_notificacion (empresa_id, evento, canal, activo)
SELECT e.id, v.evento, v.canal, 1
FROM empresa e
JOIN (SELECT 'RECEPCION_REGISTRADA' AS evento, 'INTERNA' AS canal
      UNION ALL SELECT 'RECEPCION_REGISTRADA', 'EMAIL'
      UNION ALL SELECT 'RECEPCION_EXCEDENTE', 'INTERNA'
      UNION ALL SELECT 'RECEPCION_EXCEDENTE', 'EMAIL'
      UNION ALL SELECT 'RECEPCION_CERRADA', 'INTERNA'
      UNION ALL SELECT 'RECEPCION_CERRADA', 'EMAIL') v;
