-- Spec agents/specs/010-endurecimiento-e2e: alerta cuando un cajero llega en un día al umbral de anulaciones o de
-- descuentos autorizados. Decisión de la usuaria (2026-11-20): 3 anulaciones y 5 descuentos por defecto; avisa al
-- dueño y a los supervisores de ventas, una sola vez por cajero, tipo y día.
ALTER TABLE empresa
    ADD COLUMN umbral_anulaciones_dia INT NOT NULL DEFAULT 3,
    ADD COLUMN umbral_descuentos_dia  INT NOT NULL DEFAULT 5,
    ADD CONSTRAINT chk_empresa_umbrales CHECK (umbral_anulaciones_dia >= 1 AND umbral_descuentos_dia >= 1);

-- Una fila por alerta enviada: la regla única evita avisar dos veces el mismo día aunque dos cajas pasen a la vez
CREATE TABLE alerta_cajero (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id        BIGINT      NOT NULL,
    cajero_usuario_id BIGINT      NOT NULL,
    fecha             DATE        NOT NULL,
    tipo              ENUM('ANULACIONES', 'DESCUENTOS') NOT NULL,
    cantidad          INT         NOT NULL,
    creado_en         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_alerta_cajero (empresa_id, cajero_usuario_id, fecha, tipo),
    CONSTRAINT fk_alerta_cajero_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_alerta_cajero_usuario FOREIGN KEY (cajero_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
