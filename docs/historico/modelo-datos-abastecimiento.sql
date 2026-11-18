-- =============================================================
-- Sistema de Abastecimiento (Inventario + Compras + Proveedores
-- + RFQ/Cotizaciones + IA + Adjudicación + Órdenes + Pago +
-- Recepción)
-- MySQL 8.x — modelo corregido a partir del MER original.
--
-- Correcciones aplicadas respecto al MER entregado:
--   [1] bodega + stock_producto_bodega (stock por ubicación,
--       soporta traslados)
--   [2] criterios de evaluación normalizados
--       (config_pesos_evaluacion_detalle / analisis_cotizacion_detalle)
--       en vez de columnas fijas peso_x / puntaje_x
--   [3] snapshot de pesos: cada análisis referencia una versión
--       congelada de config_pesos_evaluacion (vigente_desde/hasta)
--   [4] evaluacion_proveedor con los 7 indicadores de la sección 18
--       + score_actual como rollup en proveedor
--   [5] pago.estado (PENDIENTE/REGISTRADO)
--   [6] usuario_rol como tabla N:M (un usuario puede tener varios roles)
--   [7] documento con entidad_tipo/entidad_id (FK polimórfica real)
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- NÚCLEO Y SEGURIDAD
-- =============================================================

CREATE TABLE empresa (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    razon_social      VARCHAR(200)    NOT NULL,
    nit               VARCHAR(30)     NOT NULL UNIQUE,
    direccion_principal VARCHAR(250),
    moneda_base       CHAR(3)         NOT NULL DEFAULT 'COP',
    creado_en         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE sede (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id  BIGINT UNSIGNED NOT NULL,
    nombre      VARCHAR(150) NOT NULL,
    direccion   VARCHAR(250),
    latitud     DECIMAL(10,7),
    longitud    DECIMAL(10,7),
    activo      TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

CREATE TABLE rol (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL UNIQUE,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(250)
) ENGINE=InnoDB;

CREATE TABLE permiso (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(80)  NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE rol_permiso (
    rol_id     BIGINT UNSIGNED NOT NULL,
    permiso_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    FOREIGN KEY (rol_id) REFERENCES rol(id),
    FOREIGN KEY (permiso_id) REFERENCES permiso(id)
) ENGINE=InnoDB;

CREATE TABLE usuario (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id     BIGINT UNSIGNED NOT NULL,
    nombre_completo VARCHAR(200) NOT NULL,
    correo         VARCHAR(150) NOT NULL UNIQUE,
    hash_password  VARCHAR(255) NOT NULL,
    activo         TINYINT(1) NOT NULL DEFAULT 1,
    ultimo_acceso  DATETIME NULL,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- [6] N:M real usuario-rol (un usuario puede tener varios roles)
CREATE TABLE usuario_rol (
    usuario_id BIGINT UNSIGNED NOT NULL,
    rol_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (rol_id) REFERENCES rol(id)
) ENGINE=InnoDB;

-- =============================================================
-- M02: CONFIGURACIÓN Y MAESTROS
-- =============================================================

CREATE TABLE centro_costo (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30)  NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE categoria_producto (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE unidad_medida (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo                 VARCHAR(20)  NOT NULL UNIQUE,
    nombre                 VARCHAR(80)  NOT NULL,
    factor_conversion_base DECIMAL(18,6) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE politica_aprobacion (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id   BIGINT UNSIGNED NOT NULL,
    monto_desde  DECIMAL(18,2) NOT NULL,
    monto_hasta  DECIMAL(18,2) NOT NULL,
    activo       TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- [2] catálogo de criterios (precio, calidad, entrega, garantía,
--     historial, riesgo, condiciones...) en vez de columnas fijas
CREATE TABLE criterio_evaluacion (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40)  NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- [3] cabecera versionada de la matriz de pesos
CREATE TABLE config_pesos_evaluacion (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id     BIGINT UNSIGNED NOT NULL,
    vigente_desde  DATETIME NOT NULL,
    vigente_hasta  DATETIME NULL,
    activo         TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- [2][3] detalle: peso por criterio para esa versión de la matriz.
-- Un CHECK a nivel app debe validar que SUM(peso) = 100 por config.
CREATE TABLE config_pesos_evaluacion_detalle (
    id                       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    config_pesos_evaluacion_id BIGINT UNSIGNED NOT NULL,
    criterio_evaluacion_id   BIGINT UNSIGNED NOT NULL,
    peso                     DECIMAL(5,2) NOT NULL,
    UNIQUE KEY uq_config_criterio (config_pesos_evaluacion_id, criterio_evaluacion_id),
    FOREIGN KEY (config_pesos_evaluacion_id) REFERENCES config_pesos_evaluacion(id),
    FOREIGN KEY (criterio_evaluacion_id) REFERENCES criterio_evaluacion(id)
) ENGINE=InnoDB;

-- =============================================================
-- M03: PRODUCTOS E INVENTARIO
-- =============================================================

CREATE TABLE producto (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    categoria_producto_id BIGINT UNSIGNED NOT NULL,
    unidad_medida_id     BIGINT UNSIGNED NOT NULL,
    codigo               VARCHAR(50)  NOT NULL UNIQUE,
    nombre               VARCHAR(200) NOT NULL,
    descripcion          VARCHAR(500),
    stock_minimo         DECIMAL(18,4) NOT NULL DEFAULT 0,
    stock_maximo         DECIMAL(18,4) NULL,
    costo_referencia     DECIMAL(18,4) NULL,
    activo               TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (categoria_producto_id) REFERENCES categoria_producto(id),
    FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;

-- [1] bodega/ubicación física de stock
CREATE TABLE bodega (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT UNSIGNED NOT NULL,
    nombre     VARCHAR(150) NOT NULL,
    direccion  VARCHAR(250),
    activo     TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- [1] existencia real: la fuente de verdad del stock es esta tabla,
-- actualizada únicamente por movimiento_inventario (nunca por UPDATE directo)
CREATE TABLE stock_producto_bodega (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT UNSIGNED NOT NULL,
    bodega_id   BIGINT UNSIGNED NOT NULL,
    cantidad    DECIMAL(18,4) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_producto_bodega (producto_id, bodega_id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

-- [1] soporta traslado (bodega_origen + bodega_destino) además de
-- entrada/salida/ajuste/devolución
CREATE TABLE movimiento_inventario (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    producto_id       BIGINT UNSIGNED NOT NULL,
    bodega_origen_id  BIGINT UNSIGNED NULL,
    bodega_destino_id BIGINT UNSIGNED NULL,
    usuario_id        BIGINT UNSIGNED NOT NULL,
    tipo              ENUM('ENTRADA_COMPRA','SALIDA_CONSUMO','AJUSTE_POSITIVO',
                           'AJUSTE_NEGATIVO','DEVOLUCION_PROVEEDOR',
                           'RECEPCION_PARCIAL','TRASLADO') NOT NULL,
    cantidad          DECIMAL(18,4) NOT NULL,
    stock_resultante  DECIMAL(18,4) NOT NULL,
    referencia_tipo   VARCHAR(50) NULL,
    referencia_id     BIGINT UNSIGNED NULL,
    motivo            VARCHAR(250) NULL,
    creado_en         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (bodega_origen_id) REFERENCES bodega(id),
    FOREIGN KEY (bodega_destino_id) REFERENCES bodega(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =============================================================
-- M04: PROVEEDORES
-- =============================================================

CREATE TABLE proveedor (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    razon_social   VARCHAR(200) NOT NULL,
    nit            VARCHAR(30)  NOT NULL UNIQUE,
    correo_contacto VARCHAR(150),
    hash_password  VARCHAR(255) NULL,          -- login propio del portal
    telefono       VARCHAR(30),
    latitud        DECIMAL(10,7),
    longitud       DECIMAL(10,7),
    score_actual   DECIMAL(5,2) NULL,          -- [4] rollup del score
    activo         TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- =============================================================
-- M05: SOLICITUDES DE COMPRA
-- =============================================================

CREATE TABLE solicitud_compra (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id            BIGINT UNSIGNED NOT NULL,
    centro_costo_id       BIGINT UNSIGNED NOT NULL,
    usuario_solicitante_id BIGINT UNSIGNED NOT NULL,
    consecutivo           VARCHAR(30) NOT NULL UNIQUE,
    motivo                VARCHAR(500),
    fecha_requerida       DATE NOT NULL,
    estado                ENUM('BORRADOR','PENDIENTE_APROBACION','APROBADA',
                                'RECHAZADA','EN_RFQ','ADJUDICADA','CANCELADA','CERRADA')
                          NOT NULL DEFAULT 'BORRADOR',
    creado_en             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (centro_costo_id) REFERENCES centro_costo(id),
    FOREIGN KEY (usuario_solicitante_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE solicitud_compra_linea (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id BIGINT UNSIGNED NOT NULL,
    producto_id        BIGINT UNSIGNED NOT NULL,
    cantidad           DECIMAL(18,4) NOT NULL,
    CHECK (cantidad > 0),
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

CREATE TABLE aprobacion_solicitud (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id   BIGINT UNSIGNED NOT NULL,
    usuario_aprobador_id  BIGINT UNSIGNED NOT NULL,
    politica_aprobacion_id BIGINT UNSIGNED NULL,
    decision              ENUM('APROBADA','RECHAZADA','DEVUELTA') NOT NULL,
    observaciones         VARCHAR(500),
    decidido_en           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (usuario_aprobador_id) REFERENCES usuario(id),
    FOREIGN KEY (politica_aprobacion_id) REFERENCES politica_aprobacion(id)
) ENGINE=InnoDB;

-- =============================================================
-- M06: RFQ Y COTIZACIONES
-- =============================================================

CREATE TABLE rfq (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id BIGINT UNSIGNED NOT NULL,
    usuario_id          BIGINT UNSIGNED NOT NULL,
    consecutivo         VARCHAR(30) NOT NULL UNIQUE,
    fecha_limite        DATETIME NOT NULL,
    condiciones         VARCHAR(1000),
    estado              ENUM('ABIERTA','CERRADA','ADJUDICADA','CANCELADA') NOT NULL DEFAULT 'ABIERTA',
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE rfq_linea (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id         BIGINT UNSIGNED NOT NULL,
    producto_id    BIGINT UNSIGNED NOT NULL,
    cantidad       DECIMAL(18,4) NOT NULL,
    especificaciones VARCHAR(1000),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

CREATE TABLE rfq_proveedor (
    rfq_id       BIGINT UNSIGNED NOT NULL,
    proveedor_id BIGINT UNSIGNED NOT NULL,
    invitado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rfq_id, proveedor_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

CREATE TABLE cotizacion (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id               BIGINT UNSIGNED NOT NULL,
    proveedor_id         BIGINT UNSIGNED NOT NULL,
    estado               ENUM('BORRADOR','ENVIADA','EN_ANALISIS','SELECCIONADA',
                              'NO_SELECCIONADA','RECHAZADA') NOT NULL DEFAULT 'BORRADOR',
    condiciones_comerciales VARCHAR(1000),
    documento_original_id BIGINT UNSIGNED NULL,   -- FK a documento (archivo tal cual se recibió)
    creado_en            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

CREATE TABLE cotizacion_linea (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    cotizacion_id        BIGINT UNSIGNED NOT NULL,
    rfq_linea_id         BIGINT UNSIGNED NOT NULL,
    producto_id          BIGINT UNSIGNED NOT NULL,
    precio_unitario_ofertado DECIMAL(18,4) NOT NULL,
    unidad_ofertada      VARCHAR(50) NOT NULL,
    descuento_pct        DECIMAL(5,2) NOT NULL DEFAULT 0,
    confianza_extraccion DECIMAL(4,3) NULL,      -- 0.000 - 1.000, dato de la IA
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id),
    FOREIGN KEY (rfq_linea_id) REFERENCES rfq_linea(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- =============================================================
-- M07: INTELIGENCIA Y ADJUDICACIÓN
-- =============================================================

-- [3] cabecera del análisis: referencia la versión congelada de pesos
CREATE TABLE analisis_cotizacion (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id                      BIGINT UNSIGNED NOT NULL,
    config_pesos_evaluacion_id  BIGINT UNSIGNED NOT NULL, -- snapshot usado
    creado_en                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (config_pesos_evaluacion_id) REFERENCES config_pesos_evaluacion(id)
) ENGINE=InnoDB;

-- resultado agregado por cotización (para el ranking)
CREATE TABLE analisis_cotizacion_resultado (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    analisis_cotizacion_id BIGINT UNSIGNED NOT NULL,
    cotizacion_id         BIGINT UNSIGNED NOT NULL,
    costo_total           DECIMAL(18,2) NOT NULL,
    score_final           DECIMAL(5,2) NOT NULL,
    posicion_ranking       SMALLINT UNSIGNED NOT NULL,
    UNIQUE KEY uq_analisis_cotizacion (analisis_cotizacion_id, cotizacion_id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id)
) ENGINE=InnoDB;

-- [2] puntaje por criterio y por cotización (reemplaza las columnas fijas)
CREATE TABLE analisis_cotizacion_detalle (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    analisis_cotizacion_id BIGINT UNSIGNED NOT NULL,
    cotizacion_id         BIGINT UNSIGNED NOT NULL,
    criterio_evaluacion_id BIGINT UNSIGNED NOT NULL,
    peso_aplicado         DECIMAL(5,2) NOT NULL, -- copiado del snapshot
    puntaje               DECIMAL(5,2) NOT NULL, -- 0-100
    score_ponderado        DECIMAL(6,2) NOT NULL, -- puntaje * peso_aplicado / 100
    UNIQUE KEY uq_analisis_cot_criterio (analisis_cotizacion_id, cotizacion_id, criterio_evaluacion_id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id),
    FOREIGN KEY (criterio_evaluacion_id) REFERENCES criterio_evaluacion(id)
) ENGINE=InnoDB;

CREATE TABLE adjudicacion (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id                BIGINT UNSIGNED NOT NULL,
    analisis_cotizacion_id BIGINT UNSIGNED NOT NULL,
    cotizacion_seleccionada_id BIGINT UNSIGNED NOT NULL,
    usuario_id            BIGINT UNSIGNED NOT NULL,
    sigue_recomendacion    TINYINT(1) NOT NULL,
    justificacion          VARCHAR(1000) NULL, -- obligatorio a nivel app si sigue_recomendacion = 0
    decidido_en            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_seleccionada_id) REFERENCES cotizacion(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =============================================================
-- M08-M10: ÓRDENES, PAGO Y RECEPCIÓN
-- =============================================================

CREATE TABLE orden_compra (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    adjudicacion_id BIGINT UNSIGNED NOT NULL,
    proveedor_id  BIGINT UNSIGNED NOT NULL,
    consecutivo   VARCHAR(30) NOT NULL UNIQUE,
    total         DECIMAL(18,2) NOT NULL,
    lugar_entrega VARCHAR(250) NOT NULL,
    version       INT UNSIGNED NOT NULL DEFAULT 1,
    estado        ENUM('EMITIDA','ACEPTADA_PROVEEDOR','EN_PREPARACION',
                       'DESPACHADA','RECIBIDA_PARCIAL','RECIBIDA_TOTAL',
                       'CERRADA','CANCELADA') NOT NULL DEFAULT 'EMITIDA',
    creado_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (adjudicacion_id) REFERENCES adjudicacion(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

CREATE TABLE orden_compra_linea (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT UNSIGNED NOT NULL,
    producto_id     BIGINT UNSIGNED NOT NULL,
    cantidad        DECIMAL(18,4) NOT NULL,
    precio_unitario DECIMAL(18,4) NOT NULL,
    subtotal        DECIMAL(18,2) NOT NULL,
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- [5] se agrega estado explícito (antes ausente en el MER)
CREATE TABLE pago (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT UNSIGNED NOT NULL,
    metodo          VARCHAR(50) NOT NULL,
    valor           DECIMAL(18,2) NOT NULL,
    fecha_pago      DATE NOT NULL,
    referencia      VARCHAR(100),
    estado          ENUM('PENDIENTE','REGISTRADO') NOT NULL DEFAULT 'PENDIENTE',
    comprobante_documento_id BIGINT UNSIGNED NULL, -- FK a documento
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id)
) ENGINE=InnoDB;

CREATE TABLE recepcion (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT UNSIGNED NOT NULL,
    usuario_id      BIGINT UNSIGNED NOT NULL,
    bodega_id       BIGINT UNSIGNED NOT NULL,
    recibido_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones   VARCHAR(500),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

CREATE TABLE recepcion_linea (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    recepcion_id          BIGINT UNSIGNED NOT NULL,
    orden_compra_linea_id BIGINT UNSIGNED NOT NULL,
    cantidad_recibida     DECIMAL(18,4) NOT NULL,
    cantidad_faltante     DECIMAL(18,4) NOT NULL DEFAULT 0,
    cantidad_rechazada    DECIMAL(18,4) NOT NULL DEFAULT 0,
    FOREIGN KEY (recepcion_id) REFERENCES recepcion(id),
    FOREIGN KEY (orden_compra_linea_id) REFERENCES orden_compra_linea(id)
) ENGINE=InnoDB;

-- =============================================================
-- EVALUACIÓN DE PROVEEDORES
-- =============================================================

-- [4] evento por orden, con los 7 indicadores de la sección 18 de la guía
CREATE TABLE evaluacion_proveedor (
    id                    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    proveedor_id          BIGINT UNSIGNED NOT NULL,
    orden_compra_id       BIGINT UNSIGNED NOT NULL,
    puntualidad           DECIMAL(5,2) NULL,
    cumplimiento_cantidad DECIMAL(5,2) NULL,
    incidencias           DECIMAL(5,2) NULL,
    calidad               DECIMAL(5,2) NULL,
    precio                DECIMAL(5,2) NULL,
    respuesta             DECIMAL(5,2) NULL,
    score                 DECIMAL(5,2) NULL, -- ponderación configurable de los anteriores
    creado_en             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id)
) ENGINE=InnoDB;

-- =============================================================
-- AUDITORÍA, DOCUMENTOS Y NOTIFICACIONES
-- =============================================================

-- [7] FK polimórfica real (entidad_tipo + entidad_id) en vez de
-- relaciones implícitas sin tipar
CREATE TABLE documento (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo VARCHAR(250) NOT NULL,
    ruta_storage   VARCHAR(500) NOT NULL,
    tipo_mime      VARCHAR(100) NOT NULL,
    tamano_bytes   BIGINT UNSIGNED NOT NULL,
    entidad_tipo   VARCHAR(50) NOT NULL,   -- 'COTIZACION','ORDEN_COMPRA','RECEPCION','PAGO','PROVEEDOR', etc.
    entidad_id     BIGINT UNSIGNED NOT NULL,
    subido_por_usuario_id BIGINT UNSIGNED NULL,
    creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_documento_entidad (entidad_tipo, entidad_id),
    FOREIGN KEY (subido_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE config_notificacion (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    evento VARCHAR(80) NOT NULL,
    canal  VARCHAR(30) NOT NULL, -- EMAIL, INTERNA
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE notificacion (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT UNSIGNED NOT NULL,
    evento     VARCHAR(80) NOT NULL,
    mensaje    VARCHAR(500) NOT NULL,
    leida      TINYINT(1) NOT NULL DEFAULT 0,
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE auditoria (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT UNSIGNED NULL,
    entidad    VARCHAR(80) NOT NULL,
    entidad_id BIGINT UNSIGNED NOT NULL,
    accion     VARCHAR(80) NOT NULL,
    detalle    TEXT NULL,
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auditoria_entidad (entidad, entidad_id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- FKs diferidas que apuntan a documento (creada después por dependencia circular)
ALTER TABLE cotizacion ADD CONSTRAINT fk_cotizacion_documento
    FOREIGN KEY (documento_original_id) REFERENCES documento(id);
ALTER TABLE pago ADD CONSTRAINT fk_pago_documento
    FOREIGN KEY (comprobante_documento_id) REFERENCES documento(id);

SET FOREIGN_KEY_CHECKS = 1;
