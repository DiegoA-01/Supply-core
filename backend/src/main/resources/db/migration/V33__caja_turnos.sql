-- Spec agents/specs/003-caja-turnos: cajas, turnos, libro de caja, PIN de supervisor y autorizaciones de un solo uso.

-- Tolerancia de diferencia de caja sin aprobación (decisión D6: $2.000, configurable por empresa)
ALTER TABLE empresa ADD COLUMN tolerancia_diferencia_caja DECIMAL(18,2) NOT NULL DEFAULT 2000.00;

-- PIN de supervisor (solo el hash BCrypt) con su bloqueo propio, separado del de la contraseña
ALTER TABLE usuario
    ADD COLUMN pin_supervisor_hash   VARCHAR(100) NULL,
    ADD COLUMN pin_intentos_fallidos INT          NOT NULL DEFAULT 0,
    ADD COLUMN pin_bloqueado_hasta   DATETIME     NULL;

-- Caja: un punto de venta de una sede que descuenta de una sola bodega (decisión D7)
CREATE TABLE caja (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id          BIGINT        NOT NULL,
    sede_id             BIGINT        NOT NULL,
    bodega_id           BIGINT        NOT NULL,
    nombre              VARCHAR(100)  NOT NULL,
    prefijo_consecutivo VARCHAR(10)   NOT NULL,
    tope_efectivo       DECIMAL(18,2) NOT NULL DEFAULT 0,
    activa              TINYINT(1)    NOT NULL DEFAULT 1,
    creado_en           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_caja_nombre (empresa_id, nombre),
    UNIQUE KEY uq_caja_prefijo (empresa_id, prefijo_consecutivo),
    CONSTRAINT chk_caja_tope CHECK (tope_efectivo >= 0),
    CONSTRAINT fk_caja_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_caja_sede FOREIGN KEY (sede_id) REFERENCES sede(id),
    CONSTRAINT fk_caja_bodega FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

-- Turno: se abre con una base y se cierra a ciegas. Los esperados y las diferencias se guardan al cerrar.
CREATE TABLE turno_caja (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id              BIGINT        NOT NULL,
    caja_id                 BIGINT        NOT NULL,
    cajero_usuario_id       BIGINT        NOT NULL,
    estado                  ENUM('ABIERTO', 'CERRADO', 'APROBADO') NOT NULL DEFAULT 'ABIERTO',
    abierto_en              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    base_apertura           DECIMAL(18,2) NOT NULL,
    cerrado_en              DATETIME      NULL,
    contado_efectivo        DECIMAL(18,2) NULL,
    contado_otros           JSON          NULL,
    esperado_efectivo       DECIMAL(18,2) NULL,
    esperado_otros          JSON          NULL,
    diferencia              DECIMAL(18,2) NULL,
    diferencias             JSON          NULL,
    requiere_aprobacion     TINYINT(1)    NOT NULL DEFAULT 0,
    observacion_cierre      VARCHAR(500)  NULL,
    aprobado_por_usuario_id BIGINT        NULL,
    aprobado_en             DATETIME      NULL,
    explicacion_aprobacion  VARCHAR(500)  NULL,
    version                 INT           NOT NULL DEFAULT 0,
    -- Un solo turno abierto por caja y uno por cajero: las columnas valen el id solo mientras el turno está abierto
    abierto_caja            BIGINT GENERATED ALWAYS AS (IF(estado = 'ABIERTO', caja_id, NULL)) STORED,
    abierto_cajero          BIGINT GENERATED ALWAYS AS (IF(estado = 'ABIERTO', cajero_usuario_id, NULL)) STORED,
    UNIQUE KEY uq_turno_abierto_caja (empresa_id, abierto_caja),
    UNIQUE KEY uq_turno_abierto_cajero (empresa_id, abierto_cajero),
    INDEX idx_turno_estado (empresa_id, estado, abierto_en),
    CONSTRAINT chk_turno_base CHECK (base_apertura >= 0),
    CONSTRAINT fk_turno_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_turno_caja FOREIGN KEY (caja_id) REFERENCES caja(id),
    CONSTRAINT fk_turno_cajero FOREIGN KEY (cajero_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_turno_aprobador FOREIGN KEY (aprobado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Libro de la caja: cada peso que entra (positivo) o sale (negativo). No se edita ni se borra.
CREATE TABLE movimiento_caja (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id                BIGINT        NOT NULL,
    turno_id                  BIGINT        NOT NULL,
    tipo                      ENUM('BASE', 'VENTA', 'DEVOLUCION', 'ANULACION', 'RETIRO', 'GASTO', 'INGRESO',
                                   'ABONO_CARTERA') NOT NULL,
    medio_pago                ENUM('EFECTIVO', 'TARJETA_DEBITO', 'TARJETA_CREDITO', 'TRANSFERENCIA', 'BILLETERA')
                                   NOT NULL,
    monto                     DECIMAL(18,2) NOT NULL,
    referencia_tipo           VARCHAR(30)   NULL,
    referencia_id             BIGINT        NULL,
    motivo                    VARCHAR(300)  NULL,
    soporte                   VARCHAR(100)  NULL,
    usuario_id                BIGINT        NOT NULL,
    autorizado_por_usuario_id BIGINT        NULL,
    creado_en                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_movimiento_caja_turno (turno_id, medio_pago),
    CONSTRAINT chk_movimiento_caja_monto CHECK (monto <> 0),
    CONSTRAINT fk_movimiento_caja_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_movimiento_caja_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id),
    CONSTRAINT fk_movimiento_caja_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_movimiento_caja_autoriza FOREIGN KEY (autorizado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Autorización de un solo uso: el supervisor escribe su PIN y la acción del cajero consume el permiso (2 minutos).
-- Solo se guarda el hash del token.
CREATE TABLE autorizacion_supervisor (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id             BIGINT       NOT NULL,
    supervisor_usuario_id  BIGINT       NOT NULL,
    solicitante_usuario_id BIGINT       NOT NULL,
    accion                 ENUM('ANULAR_VENTA', 'DESCUENTO', 'PRECIO_BAJO_MINIMO', 'DEVOLUCION', 'RETIRO', 'GASTO',
                                'INGRESO') NOT NULL,
    turno_id               BIGINT       NOT NULL,
    referencia_id          BIGINT       NULL,
    token_hash             CHAR(64)     NOT NULL,
    vence_en               DATETIME     NOT NULL,
    usada_en               DATETIME     NULL,
    creado_en              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_autorizacion_token (token_hash),
    CONSTRAINT fk_autorizacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_autorizacion_supervisor FOREIGN KEY (supervisor_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_autorizacion_solicitante FOREIGN KEY (solicitante_usuario_id) REFERENCES usuario(id),
    CONSTRAINT fk_autorizacion_turno FOREIGN KEY (turno_id) REFERENCES turno_caja(id)
) ENGINE=InnoDB;
