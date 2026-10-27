-- Multiempresa (SaaS) fase 1: aislamiento por empresa_id.
-- 1) Toda tabla de negocio que se consulta directamente lleva empresa_id (NOT NULL, con FK e índice).
--    Las tablas hijas (líneas, historiales, detalles) heredan la empresa de su padre y no la repiten.
-- 2) Los datos existentes pasan a la primera empresa (la sembrada en V2).
-- 3) Lo que era único en toda la base pasa a ser único POR EMPRESA (código, NIT, consecutivos...).
--    El login ahora recibe el código de la empresa, así que el correo es único por empresa.
-- Quedan globales (compartidas por todas las empresas): unidad_medida, criterio_evaluacion, rol, permiso.

-- Código público de la empresa: identifica a la empresa en rutas públicas (autorregistro de proveedores).
ALTER TABLE empresa ADD COLUMN codigo_publico VARCHAR(40) NULL;
UPDATE empresa SET codigo_publico = CONCAT('empresa-', id);
UPDATE empresa SET codigo_publico = 'demo' WHERE id = (SELECT m FROM (SELECT MIN(id) AS m FROM empresa) x);
ALTER TABLE empresa MODIFY codigo_publico VARCHAR(40) NOT NULL, ADD UNIQUE KEY uq_empresa_codigo_publico (codigo_publico);

-- categoria_producto
ALTER TABLE categoria_producto ADD COLUMN empresa_id BIGINT NULL;
UPDATE categoria_producto SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE categoria_producto MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_categoria_producto_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_categoria_producto_empresa (empresa_id);

-- proveedor
ALTER TABLE proveedor ADD COLUMN empresa_id BIGINT NULL;
UPDATE proveedor SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE proveedor MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_proveedor_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_proveedor_empresa (empresa_id);

-- producto
ALTER TABLE producto ADD COLUMN empresa_id BIGINT NULL;
UPDATE producto SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE producto MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_producto_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_producto_empresa (empresa_id);

-- stock_producto_bodega
ALTER TABLE stock_producto_bodega ADD COLUMN empresa_id BIGINT NULL;
UPDATE stock_producto_bodega SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE stock_producto_bodega MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_stock_producto_bodega_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_stock_producto_bodega_empresa (empresa_id);

-- movimiento_inventario
ALTER TABLE movimiento_inventario ADD COLUMN empresa_id BIGINT NULL;
UPDATE movimiento_inventario SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE movimiento_inventario MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_movimiento_inventario_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_movimiento_inventario_empresa (empresa_id);

-- rfq
ALTER TABLE rfq ADD COLUMN empresa_id BIGINT NULL;
UPDATE rfq SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE rfq MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_rfq_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_rfq_empresa (empresa_id);

-- cotizacion
ALTER TABLE cotizacion ADD COLUMN empresa_id BIGINT NULL;
UPDATE cotizacion SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE cotizacion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_cotizacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_cotizacion_empresa (empresa_id);

-- analisis_cotizacion
ALTER TABLE analisis_cotizacion ADD COLUMN empresa_id BIGINT NULL;
UPDATE analisis_cotizacion SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE analisis_cotizacion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_analisis_cotizacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_analisis_cotizacion_empresa (empresa_id);

-- adjudicacion
ALTER TABLE adjudicacion ADD COLUMN empresa_id BIGINT NULL;
UPDATE adjudicacion SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE adjudicacion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_adjudicacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_adjudicacion_empresa (empresa_id);

-- orden_compra
ALTER TABLE orden_compra ADD COLUMN empresa_id BIGINT NULL;
UPDATE orden_compra SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE orden_compra MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_orden_compra_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_orden_compra_empresa (empresa_id);

-- pago
ALTER TABLE pago ADD COLUMN empresa_id BIGINT NULL;
UPDATE pago SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE pago MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_pago_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_pago_empresa (empresa_id);

-- evaluacion_proveedor
ALTER TABLE evaluacion_proveedor ADD COLUMN empresa_id BIGINT NULL;
UPDATE evaluacion_proveedor SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE evaluacion_proveedor MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_evaluacion_proveedor_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_evaluacion_proveedor_empresa (empresa_id);

-- documento
ALTER TABLE documento ADD COLUMN empresa_id BIGINT NULL;
UPDATE documento SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE documento MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_documento_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_documento_empresa (empresa_id);

-- config_notificacion
ALTER TABLE config_notificacion ADD COLUMN empresa_id BIGINT NULL;
UPDATE config_notificacion SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE config_notificacion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_config_notificacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_config_notificacion_empresa (empresa_id);

-- notificacion
ALTER TABLE notificacion ADD COLUMN empresa_id BIGINT NULL;
UPDATE notificacion SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE notificacion MODIFY empresa_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_notificacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_notificacion_empresa (empresa_id);

-- Las notificaciones pertenecen a la empresa de su destinatario
UPDATE notificacion n JOIN usuario u ON u.id = n.usuario_id SET n.empresa_id = u.empresa_id;

-- Auditoría: la empresa es opcional (un intento de login con un correo desconocido no tiene empresa)
ALTER TABLE auditoria ADD COLUMN empresa_id BIGINT NULL,
    ADD CONSTRAINT fk_auditoria_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    ADD INDEX idx_auditoria_empresa (empresa_id, creado_en);
UPDATE auditoria a JOIN usuario u ON u.id = a.usuario_id SET a.empresa_id = u.empresa_id;

-- Únicos por empresa (el correo también: la misma persona puede trabajar en dos empresas)
ALTER TABLE usuario           DROP INDEX correo,      ADD UNIQUE KEY uq_usuario_empresa_correo (empresa_id, correo);
ALTER TABLE centro_costo      DROP INDEX codigo,      ADD UNIQUE KEY uq_centro_costo_empresa_codigo (empresa_id, codigo);
ALTER TABLE categoria_producto DROP INDEX nombre,     ADD UNIQUE KEY uq_categoria_empresa_nombre (empresa_id, nombre);
ALTER TABLE proveedor         DROP INDEX nit,         ADD UNIQUE KEY uq_proveedor_empresa_nit (empresa_id, nit);
ALTER TABLE producto          DROP INDEX codigo,      ADD UNIQUE KEY uq_producto_empresa_codigo (empresa_id, codigo);
ALTER TABLE solicitud_compra  DROP INDEX consecutivo, ADD UNIQUE KEY uq_solicitud_empresa_consecutivo (empresa_id, consecutivo);
ALTER TABLE rfq               DROP INDEX consecutivo, ADD UNIQUE KEY uq_rfq_empresa_consecutivo (empresa_id, consecutivo);
ALTER TABLE despacho          DROP INDEX consecutivo, ADD UNIQUE KEY uq_despacho_empresa_consecutivo (empresa_id, consecutivo);
ALTER TABLE orden_compra      DROP INDEX uq_orden_version, ADD UNIQUE KEY uq_orden_empresa_version (empresa_id, consecutivo, version);
ALTER TABLE config_notificacion DROP INDEX uq_evento_canal, ADD UNIQUE KEY uq_config_empresa_evento_canal (empresa_id, evento, canal);

-- Consecutivos por empresa (SOL, RFQ, OC, DES): la fila se crea sola al usarse por primera vez
ALTER TABLE consecutivo ADD COLUMN empresa_id BIGINT NULL;
UPDATE consecutivo SET empresa_id = (SELECT MIN(id) FROM empresa);
ALTER TABLE consecutivo MODIFY empresa_id BIGINT NOT NULL, DROP PRIMARY KEY, ADD PRIMARY KEY (empresa_id, prefijo),
    ADD CONSTRAINT fk_consecutivo_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id);
