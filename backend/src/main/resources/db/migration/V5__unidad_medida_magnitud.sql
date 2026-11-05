-- Unidades de medida comparables: cada unidad pertenece a una magnitud y su factor dice cuántas
-- unidades base de esa magnitud equivale (UND, KG, LT y M son las bases, factor 1).
-- Solo se convierte entre unidades de la misma magnitud (kilos a litros no tiene sentido).
-- "activo" permite retirar una unidad sin borrarla (los registros históricos la conservan).

ALTER TABLE unidad_medida
    ADD COLUMN magnitud ENUM('CANTIDAD','MASA','VOLUMEN','LONGITUD') NOT NULL DEFAULT 'CANTIDAD' AFTER nombre,
    ADD COLUMN activo TINYINT(1) NOT NULL DEFAULT 1;

UPDATE unidad_medida SET magnitud = 'MASA'    WHERE codigo = 'KG';
UPDATE unidad_medida SET magnitud = 'VOLUMEN' WHERE codigo = 'LT';

INSERT INTO unidad_medida (codigo, nombre, magnitud, factor_conversion_base) VALUES
 ('CAJA24', 'Caja x 24',   'CANTIDAD', 24),
 ('G',      'Gramo',       'MASA',     0.001),
 ('ML',     'Mililitro',   'VOLUMEN',  0.001),
 ('M',      'Metro',       'LONGITUD', 1),
 ('CM',     'Centímetro',  'LONGITUD', 0.01);
