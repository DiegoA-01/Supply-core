-- Supply-Core: dos usuarios de MySQL (SaaS fase 3 / paso 19).
--
-- Por qué: la auditoría y los históricos son inmutables para la aplicación, pero si ella misma se conecta con un
-- usuario que puede hacer UPDATE y DELETE, un error o una inyección SQL podría reescribir la historia. Con dos usuarios:
--   * supply_migrator  dueño del esquema; lo usa SOLO Flyway (FLYWAY_USER / FLYWAY_PASSWORD).
--   * supply_app       lo usa la aplicación (DB_USER / DB_PASSWORD). Lee y escribe todo, pero en las tablas de
--                      registro histórico solo puede LEER e INSERTAR.
--
-- Límite que hay que explicar: root o supply_migrator todavía pueden modificar esas tablas. Blindarlas del todo exige
-- encadenar hashes (cada fila con el hash de la anterior), y eso queda fuera del alcance.
--
-- Esto NO es una migración de Flyway: depende del ambiente (usuarios y clave). Se corre a mano como administrador
-- de MySQL, y se VUELVE A CORRER después de cada migración que cree tablas nuevas (supply_app no ve una tabla
-- hasta que se le concede).
--
-- Antes de correrlo: cambiar las dos claves y, si MySQL no está en la misma máquina, el host '%' o la IP de la app.
-- Para producción se arranca la aplicación con DB_USER=supply_app y FLYWAY_USER=supply_migrator.

CREATE USER IF NOT EXISTS 'supply_migrator'@'localhost' IDENTIFIED BY 'CAMBIAR-clave-migrator';
CREATE USER IF NOT EXISTS 'supply_app'@'localhost'      IDENTIFIED BY 'CAMBIAR-clave-app';

-- Flyway necesita crear y alterar tablas, índices y llaves; la base la crea el administrador una sola vez.
CREATE DATABASE IF NOT EXISTS supply_core CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON supply_core.* TO 'supply_migrator'@'localhost';

-- MySQL no permite quitar un permiso de una tabla cuando se concedió sobre toda la base, por eso se concede tabla
-- por tabla. El procedimiento recorre las tablas que existen hoy.
USE supply_core;
DROP PROCEDURE IF EXISTS aplicar_permisos_supply_app;
DELIMITER $$
CREATE PROCEDURE aplicar_permisos_supply_app()
BEGIN
    DECLARE fin INT DEFAULT 0;
    DECLARE tabla VARCHAR(128);
    DECLARE tablas CURSOR FOR
        SELECT table_name FROM information_schema.tables
        WHERE table_schema = 'supply_core' AND table_type = 'BASE TABLE';
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET fin = 1;

    OPEN tablas;
    recorrer: LOOP
        FETCH tablas INTO tabla;
        IF fin = 1 THEN
            LEAVE recorrer;
        END IF;
        IF tabla = 'flyway_schema_history' THEN
            -- el historial de migraciones es solo de Flyway
            ITERATE recorrer;
        ELSEIF tabla IN (
                'auditoria',                     -- quién hizo qué
                'auditoria_plataforma',          -- lo mismo para la consola del dueño
                'movimiento_inventario',         -- el stock solo cambia por movimientos que no se reescriben
                'aprobacion_solicitud',          -- decisiones de aprobación
                'orden_compra_estado_historial', -- recorrido de cada orden
                'evaluacion_proveedor',          -- calificaciones: el histórico no cambia
                'documento'                      -- evidencias subidas
        ) THEN
            SET @sql = CONCAT('GRANT SELECT, INSERT ON supply_core.`', tabla, '` TO ''supply_app''@''localhost''');
        ELSE
            SET @sql = CONCAT('GRANT SELECT, INSERT, UPDATE, DELETE ON supply_core.`', tabla,
                              '` TO ''supply_app''@''localhost''');
        END IF;
        PREPARE instruccion FROM @sql;
        EXECUTE instruccion;
        DEALLOCATE PREPARE instruccion;
    END LOOP;
    CLOSE tablas;
END$$
DELIMITER ;

-- Antes de la primera migración la base está vacía y no hay nada que conceder: correr Flyway (arrancando la
-- aplicación con FLYWAY_USER=supply_migrator) y después este CALL.
CALL aplicar_permisos_supply_app();
DROP PROCEDURE aplicar_permisos_supply_app;
FLUSH PRIVILEGES;

-- Comprobación manual: con supply_app esto debe fallar con "UPDATE command denied".
--   UPDATE supply_core.auditoria SET detalle = 'x' WHERE id = 1;
