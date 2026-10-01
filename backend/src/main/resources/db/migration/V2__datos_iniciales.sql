-- Catálogos base que el sistema necesita para arrancar.
-- El usuario administrador NO va aquí: su contraseña se hashea en Java.

INSERT INTO empresa (razon_social, nit) VALUES ('Empresa Demo S.A.S.', '900000000-1');

INSERT INTO rol (codigo, nombre) VALUES
 ('ADMIN_SISTEMA',   'Administrador del sistema'),
 ('ADMIN_COMPRAS',   'Administrador de compras'),
 ('SOLICITANTE',     'Solicitante'),
 ('COMPRADOR',       'Comprador'),
 ('APROBADOR',       'Aprobador / jefe de área'),
 ('ALMACENISTA',     'Almacenista'),
 ('ADMINISTRATIVO',  'Responsable administrativo'),
 ('PROVEEDOR',       'Proveedor'),
 ('AUDITOR',         'Auditor / gerencia');

INSERT INTO criterio_evaluacion (codigo, nombre) VALUES
 ('PRECIO',    'Precio'),
 ('CALIDAD',   'Calidad'),
 ('ENTREGA',   'Tiempo de entrega'),
 ('GARANTIA',  'Garantía'),
 ('HISTORIAL', 'Historial del proveedor'),
 ('RIESGO',    'Riesgo');

INSERT INTO unidad_medida (codigo, nombre, factor_conversion_base) VALUES
 ('UND', 'Unidad', 1),
 ('DOC', 'Docena', 12),
 ('KG',  'Kilogramo', 1),
 ('LT',  'Litro', 1);

-- Matriz de pesos inicial (suma 100)
INSERT INTO config_pesos_evaluacion (empresa_id, vigente_desde) VALUES (1, NOW());
INSERT INTO config_pesos_evaluacion_detalle (config_pesos_evaluacion_id, criterio_evaluacion_id, peso)
SELECT 1, id, CASE codigo
    WHEN 'PRECIO' THEN 35 WHEN 'CALIDAD' THEN 20 WHEN 'ENTREGA' THEN 15
    WHEN 'GARANTIA' THEN 10 WHEN 'HISTORIAL' THEN 15 WHEN 'RIESGO' THEN 5 END
FROM criterio_evaluacion;
