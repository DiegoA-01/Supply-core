-- Informe de compras para el dueño y aprobación de lo que pide el jefe de compras.
--  * aprueba_dueno: si quien pide una solicitud tiene el rol aprobador de su política (p. ej. el jefe de compras),
--    como nadie aprueba lo suyo, la decide el administrador de la empresa (ADMIN_SISTEMA).
--  * informe_compras: el dueño pide un informe (PEDIDO) y el jefe de compras lo envía (ENVIADO), o el jefe lo envía
--    sin que se lo pidan. Al enviarse guarda una foto fija del periodo (contenido JSON) que no vuelve a cambiar,
--    más el análisis que escribe el jefe de compras.

ALTER TABLE solicitud_compra
    ADD COLUMN aprueba_dueno BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE informe_compras (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT       NOT NULL,
    estado                ENUM('PEDIDO','ENVIADO') NOT NULL,
    periodo_desde         DATE         NOT NULL,
    periodo_hasta         DATE         NOT NULL,
    mensaje_pedido        VARCHAR(500) NULL,
    pedido_por_usuario_id BIGINT       NULL,
    pedido_en             DATETIME     NULL,
    analisis              TEXT         NULL,
    contenido             JSON         NULL,
    enviado_por_usuario_id BIGINT      NULL,
    enviado_en            DATETIME     NULL,
    leido_en              DATETIME     NULL,
    creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_informe_empresa_estado (empresa_id, estado, creado_en),
    CONSTRAINT fk_informe_empresa  FOREIGN KEY (empresa_id)             REFERENCES empresa(id),
    CONSTRAINT fk_informe_pidio    FOREIGN KEY (pedido_por_usuario_id)  REFERENCES usuario(id),
    CONSTRAINT fk_informe_envio    FOREIGN KEY (enviado_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT chk_informe_periodo CHECK (periodo_desde <= periodo_hasta)
) ENGINE=InnoDB;
