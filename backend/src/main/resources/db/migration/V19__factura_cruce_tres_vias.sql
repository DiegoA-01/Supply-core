-- Paso 17-bis: factura del proveedor y cruce de tres vías (orden, recepción y factura).
-- El pago pasa a tener tipo: ANTICIPO (antes de recibir, con tope) o PAGO_FINAL (después de recibir, exige
-- una factura que cuadre con lo recibido). Las tolerancias y el tope se configuran por empresa.

-- Configuración por empresa
ALTER TABLE empresa
    ADD COLUMN tope_anticipo_pct        DECIMAL(5,2)  NOT NULL DEFAULT 50.00,
    ADD COLUMN tolerancia_factura_pct   DECIMAL(5,2)  NOT NULL DEFAULT 1.00,
    ADD COLUMN tolerancia_factura_monto DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    ADD CONSTRAINT chk_empresa_factura_config CHECK (tope_anticipo_pct BETWEEN 0 AND 100
        AND tolerancia_factura_pct BETWEEN 0 AND 100 AND tolerancia_factura_monto >= 0);

-- Tipo de pago. Lo ya registrado se considera anticipo (todavía no existía la factura).
ALTER TABLE pago
    ADD COLUMN tipo ENUM('ANTICIPO','PAGO_FINAL') NOT NULL DEFAULT 'ANTICIPO' AFTER orden_compra_id,
    ADD INDEX idx_pago_orden_tipo (orden_compra_id, tipo, estado);

-- Factura del proveedor de una orden. Se compara con lo recibido y aceptado (cantidad aceptada x precio de la
-- orden + impuesto) y queda CUADRADA si la diferencia está dentro de la tolerancia, o EN_REVISION si no.
-- Una factura EN_REVISION la aprueba compras con motivo (APROBADA). Una factura equivocada se ANULA con motivo.
CREATE TABLE factura_proveedor (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id              BIGINT        NOT NULL,
    orden_compra_id         BIGINT        NOT NULL,
    proveedor_id            BIGINT        NOT NULL,
    usuario_id              BIGINT        NOT NULL,
    numero                  VARCHAR(60)   NOT NULL,
    fecha_factura           DATE          NOT NULL,
    subtotal                DECIMAL(18,2) NOT NULL,
    impuestos               DECIMAL(18,2) NOT NULL,
    total                   DECIMAL(18,2) NOT NULL,
    esperado_subtotal       DECIMAL(18,2) NOT NULL,
    esperado_impuestos      DECIMAL(18,2) NOT NULL,
    esperado_total          DECIMAL(18,2) NOT NULL,
    diferencia              DECIMAL(18,2) NOT NULL,
    estado                  ENUM('CUADRADA','EN_REVISION','APROBADA','ANULADA') NOT NULL,
    documento_id            BIGINT        NULL,
    motivo_aprobacion       VARCHAR(500)  NULL,
    aprobado_por_usuario_id BIGINT        NULL,
    aprobado_en             DATETIME      NULL,
    motivo_anulacion        VARCHAR(500)  NULL,
    anulado_por_usuario_id  BIGINT        NULL,
    anulado_en              DATETIME      NULL,
    creado_en               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- el número solo cuenta mientras la factura no esté anulada: se puede volver a registrar bien
    numero_activo           VARCHAR(60) GENERATED ALWAYS AS (IF(estado = 'ANULADA', NULL, numero)) VIRTUAL,
    UNIQUE KEY uq_factura_empresa_proveedor_numero (empresa_id, proveedor_id, numero_activo),
    INDEX idx_factura_orden (orden_compra_id, estado),
    INDEX idx_factura_estado (empresa_id, estado),
    CONSTRAINT fk_factura_empresa    FOREIGN KEY (empresa_id)              REFERENCES empresa(id),
    CONSTRAINT fk_factura_orden      FOREIGN KEY (orden_compra_id)         REFERENCES orden_compra(id),
    CONSTRAINT fk_factura_proveedor  FOREIGN KEY (proveedor_id)            REFERENCES proveedor(id),
    CONSTRAINT fk_factura_usuario    FOREIGN KEY (usuario_id)              REFERENCES usuario(id),
    CONSTRAINT fk_factura_documento  FOREIGN KEY (documento_id)            REFERENCES documento(id),
    CONSTRAINT fk_factura_aprobador  FOREIGN KEY (aprobado_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_factura_anulador   FOREIGN KEY (anulado_por_usuario_id)  REFERENCES usuario(id),
    CONSTRAINT chk_factura_montos    CHECK (subtotal >= 0 AND impuestos >= 0 AND total > 0 AND total = subtotal + impuestos),
    CONSTRAINT chk_factura_aprobada  CHECK (estado <> 'APROBADA' OR motivo_aprobacion IS NOT NULL),
    CONSTRAINT chk_factura_anulada   CHECK (estado <> 'ANULADA' OR motivo_anulacion IS NOT NULL)
) ENGINE=InnoDB;

-- Eventos nuevos de notificación, sembrados para cada empresa existente
INSERT INTO config_notificacion (empresa_id, evento, canal, activo)
SELECT e.id, v.evento, v.canal, 1
FROM empresa e
JOIN (SELECT 'FACTURA_EN_REVISION' AS evento, 'INTERNA' AS canal
      UNION ALL SELECT 'FACTURA_EN_REVISION', 'EMAIL'
      UNION ALL SELECT 'FACTURA_DECIDIDA', 'INTERNA'
      UNION ALL SELECT 'FACTURA_DECIDIDA', 'EMAIL') v;
