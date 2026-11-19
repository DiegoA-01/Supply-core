-- Fase F1 del módulo de Ventas (SPEC_VENTAS.md §6.1): impuestos de venta, listas de precios con historial y margen
-- teórico. Mismas tablas y nombres que la spec, para que el punto de venta (fases siguientes) las use tal cual.
-- El margen de esta fase se calcula con el costo de referencia (último precio de compra); el costo promedio llega en F8.

-- 1) Módulo VENTAS en los planes Profesional y Empresarial
INSERT INTO modulo (codigo, nombre, descripcion) VALUES
    ('VENTAS', 'Ventas', 'Precios de venta, listas de precios, impuestos y margen');
INSERT INTO plan_modulo (plan_id, modulo_codigo)
SELECT id, 'VENTAS' FROM plan WHERE codigo IN ('PROFESIONAL', 'EMPRESARIAL');

-- 2) Impuestos de venta por empresa (IVA 19 %, IVA 5 %, exento, excluido)
CREATE TABLE tarifa_impuesto (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT       NOT NULL,
    codigo     VARCHAR(20)  NOT NULL,
    nombre     VARCHAR(100) NOT NULL,
    porcentaje DECIMAL(5,2) NOT NULL,
    activo     TINYINT(1)   NOT NULL DEFAULT 1,
    UNIQUE KEY uq_tarifa_impuesto (empresa_id, codigo),
    CONSTRAINT chk_tarifa_porcentaje CHECK (porcentaje >= 0 AND porcentaje <= 100),
    CONSTRAINT fk_tarifa_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

INSERT INTO tarifa_impuesto (empresa_id, codigo, nombre, porcentaje)
SELECT e.id, t.codigo, t.nombre, t.porcentaje FROM empresa e
JOIN (SELECT 'IVA19' AS codigo, 'IVA 19 %' AS nombre, 19.00 AS porcentaje
      UNION ALL SELECT 'IVA5', 'IVA 5 %', 5.00
      UNION ALL SELECT 'EXENTO', 'Exento', 0.00
      UNION ALL SELECT 'EXCLUIDO', 'Excluido', 0.00) t;

-- 3) Datos de venta del producto
ALTER TABLE producto
    ADD COLUMN codigo_barras VARCHAR(50) NULL,
    ADD COLUMN tarifa_impuesto_id BIGINT NULL,
    ADD COLUMN vendible TINYINT(1) NOT NULL DEFAULT 1,
    ADD COLUMN precio_incluye_impuesto TINYINT(1) NOT NULL DEFAULT 1,
    ADD UNIQUE KEY uq_producto_codigo_barras (empresa_id, codigo_barras),
    ADD CONSTRAINT fk_producto_tarifa FOREIGN KEY (tarifa_impuesto_id) REFERENCES tarifa_impuesto(id);

-- Los productos existentes quedan con IVA 19 % (lo más común en ferretería); se cambia por producto.
UPDATE producto p JOIN tarifa_impuesto t ON t.empresa_id = p.empresa_id AND t.codigo = 'IVA19'
SET p.tarifa_impuesto_id = t.id;

-- 4) Listas de precios: "General" obligatoria en cada empresa
CREATE TABLE lista_precio (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT       NOT NULL,
    nombre     VARCHAR(100) NOT NULL,
    es_general TINYINT(1)   NOT NULL DEFAULT 0,
    activo     TINYINT(1)   NOT NULL DEFAULT 1,
    UNIQUE KEY uq_lista_precio_nombre (empresa_id, nombre),
    CONSTRAINT fk_lista_precio_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

INSERT INTO lista_precio (empresa_id, nombre, es_general) SELECT id, 'General', 1 FROM empresa;

-- 5) Historial de precios: nunca se sobrescribe; cambiar un precio cierra la vigencia del anterior
CREATE TABLE precio_producto (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT        NOT NULL,
    lista_precio_id       BIGINT        NOT NULL,
    producto_id           BIGINT        NOT NULL,
    unidad_medida_id      BIGINT        NOT NULL,
    precio                DECIMAL(18,2) NOT NULL,
    precio_minimo         DECIMAL(18,2) NULL,
    vigente_desde         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    vigente_hasta         DATETIME      NULL,
    creado_por_usuario_id BIGINT        NOT NULL,
    motivo                VARCHAR(300)  NULL,
    INDEX idx_precio_vigente (empresa_id, lista_precio_id, producto_id, vigente_hasta),
    CONSTRAINT chk_precio_positivo CHECK (precio > 0),
    CONSTRAINT chk_precio_minimo CHECK (precio_minimo IS NULL OR (precio_minimo > 0 AND precio_minimo <= precio)),
    CONSTRAINT fk_precio_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_precio_lista FOREIGN KEY (lista_precio_id) REFERENCES lista_precio(id),
    CONSTRAINT fk_precio_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_precio_unidad FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id),
    CONSTRAINT fk_precio_usuario FOREIGN KEY (creado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- 6) El dueño fija los precios; puede delegarlo al jefe de compras (decisión D8 de la spec)
ALTER TABLE empresa ADD COLUMN precios_delegados_compras TINYINT(1) NOT NULL DEFAULT 0;
