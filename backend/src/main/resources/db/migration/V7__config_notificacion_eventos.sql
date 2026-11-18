-- Paso 8: eventos de notificación conocidos y sus canales (INTERNA = bandeja en la app, EMAIL = correo).
-- Un evento/canal sin fila se considera activo; ADMIN_SISTEMA puede desactivarlo desde la API.
INSERT INTO config_notificacion (evento, canal, activo) VALUES
    ('PROVEEDOR_PENDIENTE', 'INTERNA', 1),
    ('PROVEEDOR_PENDIENTE', 'EMAIL',   1),
    ('STOCK_BAJO_MINIMO',   'INTERNA', 1),
    ('STOCK_BAJO_MINIMO',   'EMAIL',   1);
