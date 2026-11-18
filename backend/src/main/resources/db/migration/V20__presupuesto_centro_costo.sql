-- Paso 18-a: presupuesto por centro de costo y período (mes).
-- Al enviar una solicitud se compara su monto estimado con lo ya comprometido en el mes de la fecha requerida.
-- Si no hay presupuesto definido para ese centro y mes, no hay control. Si se excede, la solicitud solo se envía
-- con una justificación y pasa por la política de mayor tramo (excepción auditada).

CREATE TABLE presupuesto (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT        NOT NULL,
    centro_costo_id       BIGINT        NOT NULL,
    anio                  SMALLINT      NOT NULL,
    mes                   TINYINT       NOT NULL,
    monto                 DECIMAL(18,2) NOT NULL,
    creado_por_usuario_id BIGINT        NOT NULL,
    creado_en             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en        DATETIME      NULL,
    UNIQUE KEY uq_presupuesto_periodo (empresa_id, centro_costo_id, anio, mes),
    INDEX idx_presupuesto_centro (centro_costo_id, anio, mes),
    CONSTRAINT fk_presupuesto_empresa FOREIGN KEY (empresa_id)            REFERENCES empresa(id),
    CONSTRAINT fk_presupuesto_centro  FOREIGN KEY (centro_costo_id)       REFERENCES centro_costo(id),
    CONSTRAINT fk_presupuesto_usuario FOREIGN KEY (creado_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT chk_presupuesto_valores CHECK (mes BETWEEN 1 AND 12 AND anio BETWEEN 2000 AND 2100 AND monto > 0)
) ENGINE=InnoDB;

-- Marca de excepción en la solicitud: se excedió el presupuesto o se repitió un producto ya pedido.
ALTER TABLE solicitud_compra
    ADD COLUMN excede_presupuesto      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN justificacion_excepcion VARCHAR(500) NULL,
    ADD CONSTRAINT chk_solicitud_excepcion CHECK (excede_presupuesto = FALSE OR justificacion_excepcion IS NOT NULL);

-- Consulta del comprometido por centro de costo y fecha requerida.
ALTER TABLE solicitud_compra
    ADD INDEX idx_solicitud_centro_fecha (centro_costo_id, fecha_requerida, estado);
