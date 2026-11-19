-- Spec agents/specs/002-ventas-clientes: clientes de la empresa, con el consumidor final y la autorización de datos.

CREATE TABLE cliente (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id          BIGINT        NOT NULL,
    tipo_documento      ENUM('CC', 'NIT', 'CE', 'PAS') NOT NULL,
    numero_documento    VARCHAR(20)   NOT NULL,
    nombre              VARCHAR(200)  NOT NULL,
    telefono            VARCHAR(30)   NULL,
    correo              VARCHAR(150)  NULL,
    direccion           VARCHAR(250)  NULL,
    lista_precio_id     BIGINT        NULL,
    cupo_credito        DECIMAL(18,2) NOT NULL DEFAULT 0,
    plazo_dias          INT           NOT NULL DEFAULT 0,
    es_consumidor_final TINYINT(1)    NOT NULL DEFAULT 0,
    activo              TINYINT(1)    NOT NULL DEFAULT 1,
    -- Fecha en que el cliente autorizó el tratamiento de sus datos (Ley 1581 de 2012). Sin ella no se guarda contacto.
    acepta_datos_en     DATETIME      NULL,
    creado_en           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- Vale 1 solo en el consumidor final: con el UNIQUE, cada empresa tiene uno y solo uno.
    consumidor_unico    TINYINT GENERATED ALWAYS AS (IF(es_consumidor_final = 1, 1, NULL)) STORED,
    UNIQUE KEY uq_cliente_documento (empresa_id, tipo_documento, numero_documento),
    UNIQUE KEY uq_cliente_consumidor_final (empresa_id, consumidor_unico),
    INDEX idx_cliente_nombre (empresa_id, nombre),
    CONSTRAINT chk_cliente_cupo CHECK (cupo_credito >= 0),
    CONSTRAINT chk_cliente_plazo CHECK (plazo_dias BETWEEN 0 AND 365),
    CONSTRAINT fk_cliente_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_cliente_lista FOREIGN KEY (lista_precio_id) REFERENCES lista_precio(id)
) ENGINE=InnoDB;

-- Consumidor final de cada empresa que ya existe (las nuevas lo obtienen en su primera consulta de clientes)
INSERT INTO cliente (empresa_id, tipo_documento, numero_documento, nombre, es_consumidor_final)
SELECT id, 'CC', '222222222222', 'Consumidor final', 1 FROM empresa;
