-- SaaS fase 3: seguridad restante.
--  * version_sesion: cada vez que se cambia la clave o se desactiva a un usuario sube en uno; el JWT lleva la
--    versión con la que se emitió y deja de valer cuando ya no coincide (corta la sesión sin esperar los 15 min).
--  * mfa_*: segundo factor TOTP de los administradores de empresa (mismo esquema que usuario_plataforma).
--  * recuperacion_password: tokens de un solo uso para restablecer la clave; en la base solo queda el hash.

ALTER TABLE usuario
    ADD COLUMN version_sesion  INT         NOT NULL DEFAULT 0,
    ADD COLUMN mfa_secreto     VARCHAR(64) NULL,
    ADD COLUMN mfa_activo      TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN mfa_ultimo_paso BIGINT      NULL;

CREATE TABLE recuperacion_password (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT      NOT NULL,
    usuario_id  BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    expira_en   DATETIME    NOT NULL,
    usado_en    DATETIME    NULL,
    creado_en   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_recuperacion_token (token_hash),
    INDEX idx_recuperacion_usuario (usuario_id, creado_en),
    CONSTRAINT fk_recuperacion_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario(id)
);
