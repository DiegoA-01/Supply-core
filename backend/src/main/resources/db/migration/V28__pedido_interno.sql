-- Pedido interno ("Necesito este producto"): cualquier usuario interno avisa a compras de algo que hace falta, sin
-- tener que armar una solicitud. Compras decide qué hacer (trasladar, comprar por contrato o cotizar) y responde.
-- Nunca lo ven los proveedores: es información interna del negocio.

CREATE TABLE pedido_interno (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id              BIGINT        NOT NULL,
    consecutivo             VARCHAR(30)   NOT NULL,
    usuario_id              BIGINT        NOT NULL,
    producto_id             BIGINT        NULL,
    descripcion             VARCHAR(300)  NULL,
    cantidad                DECIMAL(18,4) NULL,
    sede_id                 BIGINT        NULL,
    nota                    VARCHAR(500)  NULL,
    estado                  ENUM('PENDIENTE','EN_GESTION','ATENDIDO','DESCARTADO') NOT NULL DEFAULT 'PENDIENTE',
    respuesta               VARCHAR(500)  NULL,
    atendido_por_usuario_id BIGINT        NULL,
    atendido_en             DATETIME      NULL,
    creado_en               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_pedido_interno_consecutivo (empresa_id, consecutivo),
    INDEX idx_pedido_interno_estado (empresa_id, estado, creado_en),
    INDEX idx_pedido_interno_usuario (empresa_id, usuario_id, creado_en),
    CONSTRAINT chk_pedido_interno_que CHECK (producto_id IS NOT NULL OR descripcion IS NOT NULL),
    CONSTRAINT chk_pedido_interno_cantidad CHECK (cantidad IS NULL OR cantidad > 0),
    CONSTRAINT fk_pedido_interno_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_pedido_interno_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_pedido_interno_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_pedido_interno_sede FOREIGN KEY (sede_id) REFERENCES sede(id),
    CONSTRAINT fk_pedido_interno_atendio FOREIGN KEY (atendido_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;
