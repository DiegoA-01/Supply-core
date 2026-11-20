-- Spec agents/specs/006-anulacion-devolucion: anular una venta del turno abierto y recibir devoluciones de clientes,
-- con el stock y la caja volviendo exactos. Nada se borra: todo queda como movimiento.

-- Dos entradas nuevas de inventario:
-- ANULACION_VENTA    = devuelve al stock lo de una venta anulada en su turno.
-- DEVOLUCION_CLIENTE = el cliente regresa mercancía en buen estado (o a la bodega de averías si llega dañada).
ALTER TABLE movimiento_inventario
    MODIFY COLUMN tipo ENUM('ENTRADA_COMPRA','SALIDA_CONSUMO','AJUSTE_POSITIVO','AJUSTE_NEGATIVO',
                            'DEVOLUCION_PROVEEDOR','TRASLADO','SALIDA_VENTA','ANULACION_DESPACHO',
                            'ANULACION_VENTA','DEVOLUCION_CLIENTE') NOT NULL;

-- Quién autorizó la anulación (la venta ya guarda quién autorizó sus descuentos)
ALTER TABLE venta ADD COLUMN anulacion_autorizada_por_usuario_id BIGINT NULL,
    ADD CONSTRAINT fk_venta_anulacion_autoriza FOREIGN KEY (anulacion_autorizada_por_usuario_id) REFERENCES usuario(id);

-- Bodega a la que va la mercancía devuelta en mal estado (opcional: sin ella, lo dañado no reingresa)
ALTER TABLE empresa ADD COLUMN bodega_averias_id BIGINT NULL,
    ADD CONSTRAINT fk_empresa_bodega_averias FOREIGN KEY (bodega_averias_id) REFERENCES bodega(id);

-- Devolución de cliente: su consecutivo, motivo, forma de reembolso y quién la autorizó
CREATE TABLE devolucion_venta (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id                BIGINT        NOT NULL,
    consecutivo               VARCHAR(20)   NOT NULL,
    venta_id                  BIGINT        NOT NULL,
    turno_id                  BIGINT        NOT NULL,
    motivo                    VARCHAR(300)  NOT NULL,
    forma_reembolso           ENUM('EFECTIVO', 'MISMO_MEDIO', 'SALDO_A_FAVOR') NOT NULL,
    medio_reembolso           ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA') NULL,
    total_reembolso           DECIMAL(18,2) NOT NULL,
    usuario_id                BIGINT        NOT NULL,
    autorizado_por_usuario_id BIGINT        NOT NULL,
    creado_en                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_devolucion_consecutivo (empresa_id, consecutivo),
    INDEX idx_devolucion_venta (venta_id),
    CONSTRAINT chk_devolucion_total CHECK (total_reembolso >= 0),
    CONSTRAINT fk_devolucion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_devolucion_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_devolucion_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_devolucion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_devolucion_autoriza FOREIGN KEY (autorizado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Cada línea devuelta: cuánto, en qué estado y el valor tomado de la foto fija de la línea vendida
CREATE TABLE devolucion_venta_linea (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    devolucion_id            BIGINT        NOT NULL,
    venta_linea_id           BIGINT        NOT NULL,
    cantidad                 DECIMAL(18,4) NOT NULL,
    cantidad_unidad_producto DECIMAL(18,4) NOT NULL,
    estado_producto          ENUM('BUENO', 'DANADO') NOT NULL,
    bodega_id                BIGINT        NULL,
    valor                    DECIMAL(18,2) NOT NULL,
    CONSTRAINT chk_devolucion_linea CHECK (cantidad > 0 AND valor >= 0),
    CONSTRAINT fk_devolucion_linea_devolucion FOREIGN KEY (devolucion_id) REFERENCES devolucion_venta(id),
    CONSTRAINT fk_devolucion_linea_venta_linea FOREIGN KEY (venta_linea_id) REFERENCES venta_linea(id),
    CONSTRAINT fk_devolucion_linea_bodega FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

-- Saldo a favor del cliente: cada abono (positivo) o consumo (negativo, llega con la cartera, spec 007)
CREATE TABLE saldo_favor_cliente (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id    BIGINT        NOT NULL,
    cliente_id    BIGINT        NOT NULL,
    devolucion_id BIGINT        NULL,
    monto         DECIMAL(18,2) NOT NULL,
    motivo        VARCHAR(300)  NULL,
    creado_en     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_saldo_favor_cliente (empresa_id, cliente_id),
    CONSTRAINT chk_saldo_favor_monto CHECK (monto <> 0),
    CONSTRAINT fk_saldo_favor_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_saldo_favor_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_saldo_favor_devolucion FOREIGN KEY (devolucion_id) REFERENCES devolucion_venta(id)
) ENGINE=InnoDB;
