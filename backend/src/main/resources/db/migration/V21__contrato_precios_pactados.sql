-- Paso 18-b: contratos con proveedores y precios pactados.
-- Un contrato tiene vigencia y, por producto, un precio y un tope de cantidad. Con un contrato vigente que cubra
-- toda una solicitud APROBADA se puede emitir una orden directa, sin RFQ, al precio pactado. La orden directa no
-- nace de una adjudicación: guarda el contrato y la solicitud de origen.

CREATE TABLE contrato_proveedor (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id             BIGINT       NOT NULL,
    proveedor_id           BIGINT       NOT NULL,
    numero                 VARCHAR(40)  NOT NULL,
    descripcion            VARCHAR(250) NULL,
    vigente_desde          DATE         NOT NULL,
    vigente_hasta          DATE         NOT NULL,
    estado                 ENUM('ACTIVO','ANULADO') NOT NULL DEFAULT 'ACTIVO',
    motivo_anulacion       VARCHAR(500) NULL,
    anulado_por_usuario_id BIGINT       NULL,
    anulado_en             DATETIME     NULL,
    creado_por_usuario_id  BIGINT       NOT NULL,
    creado_en              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_contrato_empresa_numero (empresa_id, numero),
    INDEX idx_contrato_proveedor (proveedor_id, estado, vigente_desde, vigente_hasta),
    CONSTRAINT fk_contrato_empresa   FOREIGN KEY (empresa_id)             REFERENCES empresa(id),
    CONSTRAINT fk_contrato_proveedor FOREIGN KEY (proveedor_id)           REFERENCES proveedor(id),
    CONSTRAINT fk_contrato_anulador  FOREIGN KEY (anulado_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_contrato_creador   FOREIGN KEY (creado_por_usuario_id)  REFERENCES usuario(id),
    CONSTRAINT chk_contrato_fechas   CHECK (vigente_desde <= vigente_hasta),
    CONSTRAINT chk_contrato_anulado  CHECK (estado <> 'ANULADO' OR motivo_anulacion IS NOT NULL)
) ENGINE=InnoDB;

-- Precio pactado de un producto (por unidad del producto, sin impuesto). cantidad_maxima NULL = sin tope.
CREATE TABLE contrato_precio (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    contrato_id     BIGINT        NOT NULL,
    producto_id     BIGINT        NOT NULL,
    precio_unitario DECIMAL(18,4) NOT NULL,
    impuesto_pct    DECIMAL(5,2)  NOT NULL DEFAULT 0,
    cantidad_maxima DECIMAL(18,4) NULL,
    UNIQUE KEY uq_contrato_producto (contrato_id, producto_id),
    CONSTRAINT fk_cprecio_contrato FOREIGN KEY (contrato_id) REFERENCES contrato_proveedor(id),
    CONSTRAINT fk_cprecio_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT chk_cprecio_valores CHECK (precio_unitario > 0 AND impuesto_pct BETWEEN 0 AND 100
        AND (cantidad_maxima IS NULL OR cantidad_maxima > 0))
) ENGINE=InnoDB;

-- La orden puede nacer de una adjudicación (flujo con RFQ) o de un contrato (orden directa).
ALTER TABLE orden_compra
    MODIFY COLUMN adjudicacion_id BIGINT NULL,
    ADD COLUMN contrato_id  BIGINT NULL,
    ADD COLUMN solicitud_id BIGINT NULL,
    ADD INDEX idx_orden_contrato (contrato_id),
    ADD INDEX idx_orden_solicitud (solicitud_id),
    ADD CONSTRAINT fk_orden_contrato  FOREIGN KEY (contrato_id)  REFERENCES contrato_proveedor(id),
    ADD CONSTRAINT fk_orden_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud_compra(id),
    ADD CONSTRAINT chk_orden_origen CHECK (adjudicacion_id IS NOT NULL
        OR (contrato_id IS NOT NULL AND solicitud_id IS NOT NULL));

-- La línea recuerda de qué precio pactado viene: así se descuenta del tope del contrato.
ALTER TABLE orden_compra_linea
    ADD COLUMN contrato_precio_id BIGINT NULL,
    ADD INDEX idx_ocl_contrato_precio (contrato_precio_id),
    ADD CONSTRAINT fk_ocl_contrato_precio FOREIGN KEY (contrato_precio_id) REFERENCES contrato_precio(id);
