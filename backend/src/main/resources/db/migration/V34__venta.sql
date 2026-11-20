-- Spec agents/specs/004-venta-backend: la venta en una sola transacción, con su foto fija de precios, impuestos,
-- descuentos y costos; los pagos; el pedido de venta sin cobrar y el límite de descuento por rol.
-- Decisiones: D1 nunca stock negativo, D3 redondeo del efectivo a $50, D4 sin crédito todavía.

-- Consecutivo por caja (C1-000123): la fila de la caja se bloquea al vender, así no hay huecos ni repetidos.
ALTER TABLE caja ADD COLUMN ultimo_consecutivo BIGINT NOT NULL DEFAULT 0;

-- Límite de descuento sin autorización por rol y empresa. Sin fila, rige el valor por defecto del sistema
-- (cajero 5 %, supervisor de ventas 15 %, vendedor 5 %; el dueño no tiene límite).
CREATE TABLE limite_descuento_rol (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id           BIGINT       NOT NULL,
    rol_codigo           VARCHAR(30)  NOT NULL,
    descuento_maximo_pct DECIMAL(5,2) NOT NULL,
    UNIQUE KEY uq_limite_descuento (empresa_id, rol_codigo),
    CONSTRAINT chk_limite_descuento CHECK (descuento_maximo_pct >= 0 AND descuento_maximo_pct <= 100),
    CONSTRAINT fk_limite_descuento_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- Pedido de venta: lo arma el vendedor sin cobrar. No toca stock ni caja; al cobrarlo se recalcula todo.
CREATE TABLE pedido_venta (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id          BIGINT       NOT NULL,
    consecutivo         VARCHAR(20)  NOT NULL,
    cliente_id          BIGINT       NOT NULL,
    vendedor_usuario_id BIGINT       NOT NULL,
    estado              ENUM('PENDIENTE_COBRO', 'COBRADO', 'DESCARTADO') NOT NULL DEFAULT 'PENDIENTE_COBRO',
    nota                VARCHAR(300) NULL,
    venta_id            BIGINT       NULL,
    creado_en           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      DATETIME     NULL,
    version             INT          NOT NULL DEFAULT 0,
    UNIQUE KEY uq_pedido_venta_consecutivo (empresa_id, consecutivo),
    INDEX idx_pedido_venta_estado (empresa_id, estado, creado_en),
    CONSTRAINT fk_pedido_venta_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_pedido_venta_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_pedido_venta_vendedor FOREIGN KEY (vendedor_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE pedido_venta_linea (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_venta_id  BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    unidad_medida_id BIGINT        NOT NULL,
    cantidad         DECIMAL(18,4) NOT NULL,
    descuento_pct    DECIMAL(5,2)  NOT NULL DEFAULT 0,
    CONSTRAINT chk_pedido_venta_linea CHECK (cantidad > 0 AND descuento_pct >= 0 AND descuento_pct <= 100),
    CONSTRAINT fk_pedido_venta_linea_pedido FOREIGN KEY (pedido_venta_id) REFERENCES pedido_venta(id) ON DELETE CASCADE,
    CONSTRAINT fk_pedido_venta_linea_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_pedido_venta_linea_unidad FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;

-- Venta confirmada. Los montos los calcula Java; la pantalla solo manda productos, cantidades, descuentos y pagos.
CREATE TABLE venta (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id                BIGINT        NOT NULL,
    consecutivo               VARCHAR(20)   NOT NULL,
    caja_id                   BIGINT        NOT NULL,
    turno_id                  BIGINT        NOT NULL,
    bodega_id                 BIGINT        NOT NULL,
    cliente_id                BIGINT        NOT NULL,
    vendedor_usuario_id       BIGINT        NULL,
    cajero_usuario_id         BIGINT        NOT NULL,
    lista_precio_id           BIGINT        NOT NULL,
    pedido_venta_id           BIGINT        NULL,
    estado                    ENUM('CONFIRMADA', 'ANULADA') NOT NULL DEFAULT 'CONFIRMADA',
    subtotal                  DECIMAL(18,2) NOT NULL,
    descuento_total           DECIMAL(18,2) NOT NULL,
    base_gravable             DECIMAL(18,2) NOT NULL,
    impuestos                 DECIMAL(18,2) NOT NULL,
    total                     DECIMAL(18,2) NOT NULL,
    redondeo                  DECIMAL(18,2) NOT NULL DEFAULT 0,
    cambio                    DECIMAL(18,2) NOT NULL DEFAULT 0,
    es_credito                TINYINT(1)    NOT NULL DEFAULT 0,
    clave_idempotencia        VARCHAR(64)   NOT NULL,
    confirmada_en             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    anulada_en                DATETIME      NULL,
    anulada_por_usuario_id    BIGINT        NULL,
    autorizada_por_usuario_id BIGINT        NULL,
    motivo_anulacion          VARCHAR(300)  NULL,
    version                   INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uq_venta_consecutivo (empresa_id, consecutivo),
    UNIQUE KEY uq_venta_idempotencia (empresa_id, clave_idempotencia),
    INDEX idx_venta_turno (turno_id),
    INDEX idx_venta_fecha (empresa_id, confirmada_en),
    INDEX idx_venta_cliente (empresa_id, cliente_id),
    CONSTRAINT chk_venta_montos CHECK (subtotal >= 0 AND descuento_total >= 0 AND impuestos >= 0 AND total >= 0
                                       AND cambio >= 0),
    CONSTRAINT fk_venta_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_venta_caja FOREIGN KEY (caja_id) REFERENCES caja(id),
    CONSTRAINT fk_venta_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_venta_bodega FOREIGN KEY (bodega_id) REFERENCES bodega(id),
    CONSTRAINT fk_venta_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_venta_vendedor FOREIGN KEY (vendedor_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_venta_cajero FOREIGN KEY (cajero_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_venta_lista FOREIGN KEY (lista_precio_id) REFERENCES lista_precio(id),
    CONSTRAINT fk_venta_pedido FOREIGN KEY (pedido_venta_id) REFERENCES pedido_venta(id),
    CONSTRAINT fk_venta_anula FOREIGN KEY (anulada_por_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_venta_autoriza FOREIGN KEY (autorizada_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

ALTER TABLE pedido_venta ADD CONSTRAINT fk_pedido_venta_venta FOREIGN KEY (venta_id) REFERENCES venta(id);

-- Foto fija de cada línea: el precio, el impuesto, el descuento y el costo como estaban el día de la venta.
CREATE TABLE venta_linea (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id                  BIGINT        NOT NULL,
    producto_id               BIGINT        NOT NULL,
    unidad_medida_id          BIGINT        NOT NULL,
    cantidad                  DECIMAL(18,4) NOT NULL,
    cantidad_unidad_producto  DECIMAL(18,4) NOT NULL,
    precio_lista              DECIMAL(18,2) NOT NULL,
    precio_unitario           DECIMAL(18,2) NOT NULL,
    descuento_pct             DECIMAL(5,2)  NOT NULL DEFAULT 0,
    descuento_valor           DECIMAL(18,2) NOT NULL DEFAULT 0,
    tarifa_impuesto_pct       DECIMAL(5,2)  NOT NULL DEFAULT 0,
    precio_incluye_impuesto   TINYINT(1)    NOT NULL,
    base_gravable             DECIMAL(18,2) NOT NULL,
    impuesto                  DECIMAL(18,2) NOT NULL,
    total_linea               DECIMAL(18,2) NOT NULL,
    costo_unitario            DECIMAL(18,4) NULL,
    autorizado_por_usuario_id BIGINT        NULL,
    CONSTRAINT chk_venta_linea CHECK (cantidad > 0 AND cantidad_unidad_producto > 0 AND total_linea >= 0
                                      AND descuento_pct >= 0 AND descuento_pct <= 100),
    CONSTRAINT fk_venta_linea_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_venta_linea_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_venta_linea_unidad FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id),
    CONSTRAINT fk_venta_linea_autoriza FOREIGN KEY (autorizado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Pagos de la venta. De una tarjeta solo se guardan los últimos 4 dígitos y la autorización; nunca el número ni el CVV.
CREATE TABLE venta_pago (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    venta_id   BIGINT        NOT NULL,
    medio      ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA', 'CREDITO') NOT NULL,
    monto      DECIMAL(18,2) NOT NULL,
    recibido   DECIMAL(18,2) NULL,
    referencia VARCHAR(40)   NULL,
    ultimos4   CHAR(4)       NULL,
    CONSTRAINT chk_venta_pago CHECK (monto > 0 AND (ultimos4 IS NULL OR ultimos4 REGEXP '^[0-9]{4}$')),
    CONSTRAINT fk_venta_pago_venta FOREIGN KEY (venta_id) REFERENCES venta(id)
) ENGINE=InnoDB;
