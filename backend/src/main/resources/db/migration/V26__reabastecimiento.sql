-- Reabastecimiento por tiempo de entrega: pedir a tiempo según lo que tarda el proveedor.
--  * empresa: valores por defecto en días hábiles (lunes a viernes sin festivos de Colombia).
--      plazo_entrega_dias_defecto: cuánto tarda un pedido en llegar desde que se pide.
--      dias_seguridad_defecto:     colchón por retrasos o picos de consumo.
--      dias_cobertura_defecto:     para cuántos días (calendario) debe alcanzar lo que se pide cuando llegue.
--  * producto: plazo y seguridad propios; NULL = usa el de la empresa.
-- El cálculo (consumo diario, punto de pedido, cantidad) lo hace Java; aquí solo se guardan los parámetros.

ALTER TABLE empresa
    ADD COLUMN plazo_entrega_dias_defecto INT NOT NULL DEFAULT 5,
    ADD COLUMN dias_seguridad_defecto     INT NOT NULL DEFAULT 2,
    ADD COLUMN dias_cobertura_defecto     INT NOT NULL DEFAULT 30,
    ADD CONSTRAINT chk_empresa_plazo_entrega CHECK (plazo_entrega_dias_defecto BETWEEN 0 AND 120),
    ADD CONSTRAINT chk_empresa_dias_seguridad CHECK (dias_seguridad_defecto BETWEEN 0 AND 60),
    ADD CONSTRAINT chk_empresa_dias_cobertura CHECK (dias_cobertura_defecto BETWEEN 1 AND 365);

ALTER TABLE producto
    ADD COLUMN plazo_entrega_dias INT NULL,
    ADD COLUMN dias_seguridad     INT NULL,
    ADD CONSTRAINT chk_producto_plazo_entrega CHECK (plazo_entrega_dias IS NULL OR plazo_entrega_dias BETWEEN 0 AND 120),
    ADD CONSTRAINT chk_producto_dias_seguridad CHECK (dias_seguridad IS NULL OR dias_seguridad BETWEEN 0 AND 60);
