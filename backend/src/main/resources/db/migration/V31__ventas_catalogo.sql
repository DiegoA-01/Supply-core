-- Spec agents/specs/001-ventas-catalogo (completa la fase F1 de Ventas que empezó en V29).
-- 1) Módulos CAJA y CARTERA en los planes, 2) roles de venta, 3) un solo precio vigente por producto, lista y unidad.

-- 1) Módulos: Profesional trae VENTAS + CAJA; Empresarial, además, CARTERA
INSERT INTO modulo (codigo, nombre, descripcion) VALUES
    ('CAJA', 'Caja', 'Cajas, turnos, movimientos de caja, arqueo y cierre ciego'),
    ('CARTERA', 'Cartera', 'Ventas a crédito, cupos, abonos y edades de cartera');
INSERT INTO plan_modulo (plan_id, modulo_codigo)
SELECT id, 'CAJA' FROM plan WHERE codigo IN ('PROFESIONAL', 'EMPRESARIAL');
INSERT INTO plan_modulo (plan_id, modulo_codigo)
SELECT id, 'CARTERA' FROM plan WHERE codigo = 'EMPRESARIAL';

-- 2) Roles de venta (catálogo global; decisión D5)
INSERT INTO rol (codigo, nombre, descripcion) VALUES
    ('SUPERVISOR_VENTAS', 'Supervisor de ventas', 'Autoriza con su PIN anulaciones, descuentos, devoluciones y diferencias de caja'),
    ('VENDEDOR', 'Vendedor', 'Atiende al cliente y arma pedidos de venta sin cobrar');

-- 3) Un solo precio vigente por (empresa, lista, producto, unidad).
-- Si alguna vez quedaron dos vigentes por una carrera, se cierra el más antiguo antes de crear la regla.
UPDATE precio_producto p
JOIN (SELECT empresa_id, lista_precio_id, producto_id, unidad_medida_id, MAX(id) AS ultimo
      FROM precio_producto
      WHERE vigente_hasta IS NULL
      GROUP BY empresa_id, lista_precio_id, producto_id, unidad_medida_id
      HAVING COUNT(*) > 1) d
  ON d.empresa_id = p.empresa_id AND d.lista_precio_id = p.lista_precio_id
 AND d.producto_id = p.producto_id AND d.unidad_medida_id = p.unidad_medida_id
SET p.vigente_hasta = NOW()
WHERE p.vigente_hasta IS NULL AND p.id <> d.ultimo;

-- La columna vale 1 solo mientras el precio está vigente; los cerrados quedan en NULL y no chocan entre sí.
ALTER TABLE precio_producto
    ADD COLUMN vigente_unico TINYINT GENERATED ALWAYS AS (IF(vigente_hasta IS NULL, 1, NULL)) STORED,
    ADD UNIQUE KEY uq_precio_vigente (empresa_id, lista_precio_id, producto_id, unidad_medida_id, vigente_unico);
-- La búsqueda para vender usa los índices únicos que ya existen: (empresa_id, codigo) y (empresa_id, codigo_barras).
