-- Spec 009: cuadre diario automático entre ventas, inventario y caja.
-- Cada noche se revisa el día anterior de cada empresa y se guarda una foto fija del resultado. Volver a revisar un día
-- (después de corregir) reemplaza su fila: hay una sola por empresa y fecha.

CREATE TABLE cuadre_diario (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id           BIGINT        NOT NULL,
    fecha                DATE          NOT NULL,
    -- Lo que las ventas confirmadas del día cobraron por medios que entran a caja, y lo que la caja registró por ellas
    ventas_confirmadas   DECIMAL(18,2) NOT NULL,
    ventas_en_caja       DECIMAL(18,2) NOT NULL,
    -- Unidades vendidas (en la unidad del producto) y salidas de inventario por esas ventas
    unidades_vendidas    DECIMAL(18,4) NOT NULL,
    unidades_salida      DECIMAL(18,4) NOT NULL,
    turnos_abiertos_24h  INT           NOT NULL,
    cuadra               TINYINT(1)    NOT NULL,
    -- Ventas y turnos que no cuadran, para revisarlos uno a uno
    detalle              JSON          NULL,
    revisado_en          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cuadre_fecha (empresa_id, fecha),
    INDEX idx_cuadre_cuadra (empresa_id, cuadra, fecha),
    CONSTRAINT chk_cuadre_turnos CHECK (turnos_abiertos_24h >= 0),
    CONSTRAINT fk_cuadre_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;
