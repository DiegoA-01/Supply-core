-- Spec agents/specs/007-cartera: venta a crédito con cupo, abonos por la caja, edades de cartera, saldo a favor como
-- medio de pago y la excepción de crédito que la persona encargada da a un cliente en mora (decisión de la usuaria).

-- Dos medios nuevos en los pagos de la venta: el crédito ya existía; SALDO_FAVOR consume el saldo a favor del cliente
ALTER TABLE venta_pago
    MODIFY COLUMN medio ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA', 'CREDITO',
                             'SALDO_FAVOR') NOT NULL;

-- El saldo a favor también se consume en una venta (monto negativo con la venta que lo usó)
ALTER TABLE saldo_favor_cliente ADD COLUMN venta_id BIGINT NULL,
    ADD CONSTRAINT fk_saldo_favor_venta FOREIGN KEY (venta_id) REFERENCES venta(id);

-- Excepción de crédito: la persona encargada deja comprar a crédito a un cliente aunque tenga cuentas vencidas,
-- hasta una fecha y con motivo. No salta el cupo: para eso se sube el cupo.
ALTER TABLE cliente
    ADD COLUMN credito_excepcion_hasta DATE NULL,
    ADD COLUMN credito_excepcion_motivo VARCHAR(300) NULL,
    ADD COLUMN credito_excepcion_por_usuario_id BIGINT NULL,
    ADD CONSTRAINT fk_cliente_excepcion_usuario FOREIGN KEY (credito_excepcion_por_usuario_id) REFERENCES usuario(id);

-- Cuenta por cobrar: una por venta a crédito, con vencimiento según el plazo del cliente
CREATE TABLE cuenta_por_cobrar (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT        NOT NULL,
    venta_id   BIGINT        NOT NULL,
    cliente_id BIGINT        NOT NULL,
    valor      DECIMAL(18,2) NOT NULL,
    saldo      DECIMAL(18,2) NOT NULL,
    vence_en   DATE          NOT NULL,
    estado     ENUM('ABIERTA', 'PAGADA', 'ANULADA') NOT NULL DEFAULT 'ABIERTA',
    creado_en  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version    INT           NOT NULL DEFAULT 0,
    UNIQUE KEY uq_cuenta_venta (venta_id),
    INDEX idx_cuenta_cliente (empresa_id, cliente_id, estado, vence_en),
    CONSTRAINT chk_cuenta_saldo CHECK (valor > 0 AND saldo >= 0 AND saldo <= valor),
    CONSTRAINT fk_cuenta_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_cuenta_venta FOREIGN KEY (venta_id) REFERENCES venta(id),
    CONSTRAINT fk_cuenta_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id)
) ENGINE=InnoDB;

-- Abono de un cliente: entra a la caja del turno (ABONO_CARTERA) y se aplica a las cuentas más antiguas
CREATE TABLE abono_cliente (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT        NOT NULL,
    consecutivo VARCHAR(20)   NOT NULL,
    cliente_id  BIGINT        NOT NULL,
    turno_id    BIGINT        NOT NULL,
    valor       DECIMAL(18,2) NOT NULL,
    medio_pago  ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA') NOT NULL,
    referencia  VARCHAR(40)   NULL,
    usuario_id  BIGINT        NOT NULL,
    creado_en   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_abono_consecutivo (empresa_id, consecutivo),
    INDEX idx_abono_cliente (empresa_id, cliente_id),
    CONSTRAINT chk_abono_valor CHECK (valor > 0),
    CONSTRAINT fk_abono_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_abono_cliente FOREIGN KEY (cliente_id) REFERENCES cliente(id),
    CONSTRAINT fk_abono_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_abono_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- A qué cuenta fue cada parte del abono
CREATE TABLE abono_aplicacion (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    abono_id  BIGINT        NOT NULL,
    cuenta_id BIGINT        NOT NULL,
    valor     DECIMAL(18,2) NOT NULL,
    CONSTRAINT chk_abono_aplicacion CHECK (valor > 0),
    CONSTRAINT fk_abono_aplicacion_abono FOREIGN KEY (abono_id) REFERENCES abono_cliente(id),
    CONSTRAINT fk_abono_aplicacion_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta_por_cobrar(id)
) ENGINE=InnoDB;
