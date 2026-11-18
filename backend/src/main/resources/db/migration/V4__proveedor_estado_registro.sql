-- Autorregistro de proveedores con aprobación de ADMIN_COMPRAS.
-- El estado reemplaza a "activo" para no tener dos fuentes de verdad.
--   PENDIENTE  : se registró desde el portal, espera revisión (no puede iniciar sesión)
--   HABILITADO : aprobado, puede iniciar sesión, ser invitado a RFQ y cotizar
--   RECHAZADO  : no aprobado (motivo obligatorio)
--   SUSPENDIDO : estaba habilitado y se bloqueó (motivo obligatorio)

ALTER TABLE proveedor
    ADD COLUMN estado ENUM('PENDIENTE','HABILITADO','RECHAZADO','SUSPENDIDO')
        NOT NULL DEFAULT 'PENDIENTE' AFTER score_actual,
    ADD COLUMN motivo_estado VARCHAR(500) NULL AFTER estado,
    ADD COLUMN revisado_por_usuario_id BIGINT NULL AFTER motivo_estado,
    ADD COLUMN revisado_en DATETIME NULL AFTER revisado_por_usuario_id,
    ADD CONSTRAINT fk_proveedor_revisor FOREIGN KEY (revisado_por_usuario_id) REFERENCES usuario(id),
    DROP COLUMN activo;

CREATE INDEX idx_proveedor_estado ON proveedor (estado);

-- Categorías de demostración para poder registrar proveedores antes del módulo catálogo (paso 5)
INSERT INTO categoria_producto (nombre) VALUES
 ('Papelería y oficina'),
 ('Aseo y cafetería'),
 ('Tecnología'),
 ('Ferretería y mantenimiento');
