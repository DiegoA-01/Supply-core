-- Seguridad de sesiones (base SaaS): refresh token con rotación, bloqueo por intentos fallidos
-- y rol CAJERO para las empresas clientes.

-- Bloqueo temporal de cuenta por intentos fallidos de login
ALTER TABLE usuario
    ADD COLUMN intentos_fallidos INT      NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta   DATETIME NULL;

-- Refresh token opaco: solo se guarda su hash SHA-256 (el valor real viaja una vez al cliente).
-- Cada renovación marca el token usado y emite otro de la misma familia; si un token usado
-- se presenta de nuevo (posible robo), se revoca toda la familia.
CREATE TABLE refresh_token (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id   BIGINT      NOT NULL,
    familia      CHAR(36)    NOT NULL,
    token_hash   CHAR(64)    NOT NULL UNIQUE,
    creado_en    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expira_en    DATETIME    NOT NULL,
    usado_en     DATETIME    NULL,
    revocado_en  DATETIME    NULL,
    INDEX idx_refresh_familia (familia),
    INDEX idx_refresh_usuario (usuario_id),
    INDEX idx_refresh_expira (expira_en),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Cajero de la empresa cliente (cobra y registra ventas/entregas); lo asigna el admin de cada empresa
INSERT INTO rol (codigo, nombre, descripcion)
VALUES ('CAJERO', 'Cajero', 'Registra ventas y entregas en caja');
