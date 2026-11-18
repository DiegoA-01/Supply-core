-- SaaS fase 2: consola de plataforma, planes, módulos y suscripciones.
-- La plataforma (el dueño del software) vive aparte de las empresas: sus usuarios no llevan empresa_id, entran con
-- MFA obligatorio y no leen datos de negocio. Cada empresa tiene una suscripción (plan y estado) y los módulos que
-- el plan incluye, más excepciones por empresa.

CREATE TABLE usuario_plataforma (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    correo           VARCHAR(150) NOT NULL UNIQUE,
    nombre           VARCHAR(150) NOT NULL,
    hash_password    VARCHAR(100) NOT NULL,
    activo           TINYINT(1)   NOT NULL DEFAULT 1,
    -- secreto TOTP en base32; mfa_activo pasa a 1 al confirmar el primer código
    mfa_secreto      VARCHAR(64)  NULL,
    mfa_activo       TINYINT(1)   NOT NULL DEFAULT 0,
    -- último intervalo de 30 s aceptado: un código ya usado no sirve otra vez
    mfa_ultimo_paso  BIGINT       NULL,
    intentos_fallidos INT         NOT NULL DEFAULT 0,
    bloqueado_hasta  DATETIME     NULL,
    ultimo_acceso    DATETIME     NULL,
    creado_en        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE modulo (
    codigo      VARCHAR(40)  PRIMARY KEY,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(250) NULL
) ENGINE=InnoDB;

CREATE TABLE plan (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo    VARCHAR(30)  NOT NULL UNIQUE,
    nombre    VARCHAR(100) NOT NULL,
    activo    TINYINT(1)   NOT NULL DEFAULT 1,
    creado_en DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE plan_modulo (
    plan_id       BIGINT      NOT NULL,
    modulo_codigo VARCHAR(40) NOT NULL,
    PRIMARY KEY (plan_id, modulo_codigo),
    CONSTRAINT fk_planmod_plan   FOREIGN KEY (plan_id)       REFERENCES plan(id),
    CONSTRAINT fk_planmod_modulo FOREIGN KEY (modulo_codigo) REFERENCES modulo(codigo)
) ENGINE=InnoDB;

-- Excepción por empresa: habilitado = 1 da un módulo que el plan no incluye; 0 quita uno que sí incluye.
CREATE TABLE empresa_modulo (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id    BIGINT      NOT NULL,
    modulo_codigo VARCHAR(40) NOT NULL,
    habilitado    TINYINT(1)  NOT NULL,
    UNIQUE KEY uq_empresa_modulo (empresa_id, modulo_codigo),
    CONSTRAINT fk_empmod_empresa FOREIGN KEY (empresa_id)    REFERENCES empresa(id),
    CONSTRAINT fk_empmod_modulo  FOREIGN KEY (modulo_codigo) REFERENCES modulo(codigo)
) ENGINE=InnoDB;

CREATE TABLE suscripcion (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id     BIGINT       NOT NULL UNIQUE,
    plan_id        BIGINT       NOT NULL,
    estado         ENUM('PRUEBA','ACTIVA','MOROSA','SUSPENDIDA','CANCELADA') NOT NULL DEFAULT 'PRUEBA',
    motivo_estado  VARCHAR(500) NULL,
    desde          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizada_en DATETIME     NULL,
    INDEX idx_suscripcion_estado (estado),
    CONSTRAINT fk_suscripcion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_suscripcion_plan    FOREIGN KEY (plan_id)    REFERENCES plan(id)
) ENGINE=InnoDB;

-- Auditoría global de la plataforma (separada de la auditoría de cada empresa)
CREATE TABLE auditoria_plataforma (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_plataforma_id BIGINT      NULL,
    accion                VARCHAR(60) NOT NULL,
    empresa_id            BIGINT      NULL,
    resultado             ENUM('EXITO','FALLO') NOT NULL DEFAULT 'EXITO',
    ip                    VARCHAR(45) NULL,
    detalle               TEXT        NULL,
    creado_en             DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audplat_fecha (creado_en),
    INDEX idx_audplat_empresa (empresa_id, creado_en),
    CONSTRAINT fk_audplat_usuario FOREIGN KEY (usuario_plataforma_id) REFERENCES usuario_plataforma(id)
) ENGINE=InnoDB;

INSERT INTO modulo (codigo, nombre, descripcion) VALUES
    ('INVENTARIO',        'Inventario',                 'Productos, bodegas, stock y movimientos'),
    ('PROVEEDORES',       'Proveedores',                'Alta, aprobación y evaluación de proveedores'),
    ('COMPRAS',           'Compras',                    'Solicitudes, aprobación, presupuesto, contratos, delegación y reorden'),
    ('RFQ_COTIZACIONES',  'Cotizaciones',               'RFQ, cotizaciones, análisis y adjudicación'),
    ('IA',                'Lectura y análisis con IA',  'Lectura de cotizaciones y facturas con IA y explicación del ranking'),
    ('ORDENES_PAGOS',     'Órdenes y pagos',            'Órdenes de compra, facturas, cruce de tres vías y pagos'),
    ('RECEPCION',         'Recepción',                  'Recepción de mercancía con control de calidad'),
    ('DESPACHOS',         'Despachos',                  'Remisiones de salida'),
    ('REPORTES',          'Reportes',                   'Gasto, inventario, proveedores y estado de cuenta'),
    ('AUDITORIA',         'Auditoría',                  'Consulta de la auditoría de la empresa');

INSERT INTO plan (codigo, nombre) VALUES
    ('BASICO',       'Básico'),
    ('PROFESIONAL',  'Profesional'),
    ('EMPRESARIAL',  'Empresarial');

INSERT INTO plan_modulo (plan_id, modulo_codigo)
SELECT p.id, m.codigo FROM plan p JOIN modulo m
WHERE (p.codigo = 'BASICO' AND m.codigo IN ('INVENTARIO','PROVEEDORES','COMPRAS','RFQ_COTIZACIONES','ORDENES_PAGOS','RECEPCION'))
   OR (p.codigo = 'PROFESIONAL' AND m.codigo IN ('INVENTARIO','PROVEEDORES','COMPRAS','RFQ_COTIZACIONES','ORDENES_PAGOS','RECEPCION','DESPACHOS','REPORTES','AUDITORIA'))
   OR (p.codigo = 'EMPRESARIAL');

-- Las empresas que ya existen siguen funcionando: plan Empresarial y suscripción activa
INSERT INTO suscripcion (empresa_id, plan_id, estado)
SELECT e.id, (SELECT id FROM plan WHERE codigo = 'EMPRESARIAL'), 'ACTIVA' FROM empresa e;
