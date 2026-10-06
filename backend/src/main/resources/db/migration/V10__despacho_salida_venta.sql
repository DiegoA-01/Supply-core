-- Despacho (remisión de salida): lo que se vende o entrega sale del stock con un documento
-- de varias líneas. Sin precios ni facturación.
-- SALIDA_VENTA       = salida por despacho confirmado.
-- ANULACION_DESPACHO = entrada que devuelve al stock lo de un despacho anulado.
ALTER TABLE movimiento_inventario
    MODIFY COLUMN tipo ENUM('ENTRADA_COMPRA','SALIDA_CONSUMO','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
                            'DEVOLUCION_PROVEEDOR','TRASLADO','SALIDA_VENTA','ANULACION_DESPACHO') NOT NULL;

INSERT INTO consecutivo (prefijo) VALUES ('DES');

CREATE TABLE despacho (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id             BIGINT       NOT NULL,
    bodega_id              BIGINT       NOT NULL,
    usuario_id             BIGINT       NOT NULL,
    consecutivo            VARCHAR(30)  NOT NULL UNIQUE,
    cliente                VARCHAR(200) NULL,
    referencia_externa     VARCHAR(100) NULL,
    observaciones          VARCHAR(500) NULL,
    estado                 ENUM('BORRADOR','CONFIRMADO','ANULADO') NOT NULL DEFAULT 'BORRADOR',
    confirmado_en          DATETIME     NULL,
    anulado_en             DATETIME     NULL,
    anulado_por_usuario_id BIGINT       NULL,
    motivo_anulacion       VARCHAR(500) NULL,
    creado_en              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (anulado_por_usuario_id) REFERENCES usuario(id),
    INDEX idx_despacho_estado (estado, creado_en),
    INDEX idx_despacho_bodega (bodega_id, creado_en)
) ENGINE=InnoDB;

-- cantidad en la unidad de la línea; cantidad_base = convertida a la unidad del producto (al confirmar)
CREATE TABLE despacho_linea (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    despacho_id      BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    unidad_medida_id BIGINT        NOT NULL,
    cantidad         DECIMAL(18,4) NOT NULL,
    cantidad_base    DECIMAL(18,4) NULL,
    CHECK (cantidad > 0),
    UNIQUE KEY uq_despacho_producto (despacho_id, producto_id),
    FOREIGN KEY (despacho_id) REFERENCES despacho(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;
