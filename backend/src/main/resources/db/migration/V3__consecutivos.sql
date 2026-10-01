-- Contadores para los consecutivos legibles (usado por ConsecutivoGenerator).
CREATE TABLE consecutivo (
    prefijo VARCHAR(10) PRIMARY KEY,
    ultimo  BIGINT      NOT NULL DEFAULT 0
) ENGINE=InnoDB;

INSERT INTO consecutivo (prefijo) VALUES ('SOL'), ('RFQ'), ('OC');
