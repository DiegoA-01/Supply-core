-- Catálogos de proveedores: cada proveedor sube por su cuenta lo que vende y a qué precio. Compras cruza ese catálogo
-- con el inventario (equivalencias) y ve oportunidades: más barato que el último costo o productos que hay que pedir.
-- El proveedor solo ve su propio catálogo: nunca las equivalencias, el stock ni las oportunidades (información interna).

CREATE TABLE catalogo_item (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id          BIGINT        NOT NULL,
    proveedor_id        BIGINT        NOT NULL,
    codigo_proveedor    VARCHAR(80)   NOT NULL,
    descripcion         VARCHAR(300)  NOT NULL,
    marca               VARCHAR(100)  NULL,
    unidad              VARCHAR(30)   NULL,
    precio              DECIMAL(18,2) NOT NULL,
    incluye_iva         TINYINT(1)    NOT NULL DEFAULT 0,
    vigente_hasta       DATE          NULL,
    activo              TINYINT(1)    NOT NULL DEFAULT 1,
    producto_id         BIGINT        NULL,
    estado_equivalencia ENUM('SIN_REVISAR','SUGERIDA','CONFIRMADA','DESCARTADA') NOT NULL DEFAULT 'SIN_REVISAR',
    similitud           DECIMAL(5,2)  NULL,
    revisado_por_usuario_id BIGINT    NULL,
    actualizado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_catalogo_item (empresa_id, proveedor_id, codigo_proveedor),
    INDEX idx_catalogo_producto (empresa_id, producto_id, estado_equivalencia),
    CONSTRAINT chk_catalogo_precio CHECK (precio > 0),
    CONSTRAINT fk_catalogo_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_catalogo_proveedor FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    CONSTRAINT fk_catalogo_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_catalogo_revisor FOREIGN KEY (revisado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
