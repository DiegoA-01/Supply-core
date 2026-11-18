-- Paso 18-c: delegación de aprobación por fechas y SLA de aprobación de solicitudes.
-- Delegación: un aprobador cede sus decisiones a otro usuario interno durante un rango de fechas. El delegado
-- decide con la autoridad de los roles del delegante, pero la segregación de funciones sigue igual (nadie decide
-- lo suyo) y cada decisión guarda de qué delegación vino.
-- SLA: las solicitudes pendientes tienen un tiempo máximo (en horas, por empresa) para decidirse. Al 80 % se avisa
-- a los aprobadores; al vencer se escala a la política de mayor tramo.

CREATE TABLE delegacion_aprobacion (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT       NOT NULL,
    delegante_usuario_id  BIGINT       NOT NULL,
    delegado_usuario_id   BIGINT       NOT NULL,
    vigente_desde         DATE         NOT NULL,
    vigente_hasta         DATE         NOT NULL,
    motivo                VARCHAR(250) NULL,
    estado                ENUM('ACTIVA','REVOCADA') NOT NULL DEFAULT 'ACTIVA',
    revocada_en           DATETIME     NULL,
    revocada_por_usuario_id BIGINT     NULL,
    creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_delegacion_delegado (delegado_usuario_id, estado, vigente_desde, vigente_hasta),
    INDEX idx_delegacion_delegante (delegante_usuario_id, estado),
    CONSTRAINT fk_delegacion_empresa   FOREIGN KEY (empresa_id)               REFERENCES empresa(id),
    CONSTRAINT fk_delegacion_delegante FOREIGN KEY (delegante_usuario_id)     REFERENCES usuario(id),
    CONSTRAINT fk_delegacion_delegado  FOREIGN KEY (delegado_usuario_id)      REFERENCES usuario(id),
    CONSTRAINT fk_delegacion_revoco    FOREIGN KEY (revocada_por_usuario_id)  REFERENCES usuario(id),
    CONSTRAINT chk_delegacion_fechas   CHECK (vigente_desde <= vigente_hasta),
    CONSTRAINT chk_delegacion_personas CHECK (delegante_usuario_id <> delegado_usuario_id)
) ENGINE=InnoDB;

-- De qué delegación vino una decisión (NULL = decidió con su propio rol)
ALTER TABLE aprobacion_solicitud
    ADD COLUMN delegacion_id BIGINT NULL,
    ADD CONSTRAINT fk_aprobsol_delegacion FOREIGN KEY (delegacion_id) REFERENCES delegacion_aprobacion(id);

ALTER TABLE adjudicacion
    ADD COLUMN delegacion_id BIGINT NULL,
    ADD CONSTRAINT fk_adjudicacion_delegacion FOREIGN KEY (delegacion_id) REFERENCES delegacion_aprobacion(id);

-- SLA por empresa: horas para decidir una solicitud pendiente (0 = sin SLA)
ALTER TABLE empresa
    ADD COLUMN sla_aprobacion_horas INT NOT NULL DEFAULT 24,
    ADD CONSTRAINT chk_empresa_sla CHECK (sla_aprobacion_horas BETWEEN 0 AND 720);

-- Marcas del SLA en la solicitud: se avisa una vez y se escala una vez por cada envío
ALTER TABLE solicitud_compra
    ADD COLUMN sla_aviso_en DATETIME NULL,
    ADD COLUMN escalada_en  DATETIME NULL,
    ADD INDEX idx_solicitud_sla (estado, enviado_en);

-- Eventos nuevos de notificación, sembrados para cada empresa existente
INSERT INTO config_notificacion (empresa_id, evento, canal, activo)
SELECT e.id, v.evento, v.canal, 1
FROM empresa e
JOIN (SELECT 'SOLICITUD_SLA_POR_VENCER' AS evento, 'INTERNA' AS canal
      UNION ALL SELECT 'SOLICITUD_SLA_POR_VENCER', 'EMAIL'
      UNION ALL SELECT 'SOLICITUD_ESCALADA', 'INTERNA'
      UNION ALL SELECT 'SOLICITUD_ESCALADA', 'EMAIL'
      UNION ALL SELECT 'DELEGACION_ASIGNADA', 'INTERNA'
      UNION ALL SELECT 'DELEGACION_ASIGNADA', 'EMAIL') v;
