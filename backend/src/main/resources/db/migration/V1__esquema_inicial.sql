-- =============================================================
-- Supply-Core — Esquema inicial (MySQL 8)
-- Versión corregida del Anexo A. Cambios marcados con [Cn]:
--  [C1] usuario.centro_costo_id (precondición UC-04)
--  [C2] proveedor entra como usuario (usuario.proveedor_id);
--       se elimina proveedor.hash_password -> un solo login/JWT
--  [C3] proveedor_categoria (UC-06: proveedores habilitados)
--  [C4] politica_aprobacion con tipo y rol aprobador (UC-05/UC-09)
--  [C5] solicitud_compra.estado incluye DEVUELTA; linea con unidad
--  [C6] cotizacion con plazo, garantía, impuestos y totales
--  [C7] procesamiento_ia + extraccion_campo (confianza por dato)
--  [C8] analisis con estado/publicación, explicación IA y anomalías
--  [C9] adjudicacion con estado y aprobador (UC-09 paso 3)
--  [C10] orden_compra versionada (orden_padre_id, motivo) y
--        estado RECHAZADA_PROVEEDOR (UC-12)
--  [C11] pago.evidencia_pendiente (UC-11 flujo alterno)
--  [C12] recepcion_linea.estado para exceso en revisión (UC-13)
--  [C13] BIGINT con signo y sin FOREIGN_KEY_CHECKS=0: las tablas
--        se crean en orden de dependencia (más fácil con JPA)
-- =============================================================

-- =============================================================
-- NÚCLEO Y CONFIGURACIÓN
-- =============================================================
CREATE TABLE empresa (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    razon_social        VARCHAR(200) NOT NULL,
    nit                 VARCHAR(30)  NOT NULL UNIQUE,
    direccion_principal VARCHAR(250),
    moneda_base         CHAR(3)      NOT NULL DEFAULT 'COP',
    creado_en           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE sede (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT       NOT NULL,
    nombre     VARCHAR(150) NOT NULL,
    direccion  VARCHAR(250),
    latitud    DECIMAL(10,7),
    longitud   DECIMAL(10,7),
    activo     TINYINT(1)   NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

CREATE TABLE centro_costo (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT       NOT NULL,
    codigo     VARCHAR(30)  NOT NULL UNIQUE,
    nombre     VARCHAR(150) NOT NULL,
    activo     TINYINT(1)   NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

CREATE TABLE categoria_producto (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL UNIQUE,
    activo TINYINT(1)   NOT NULL DEFAULT 1
) ENGINE=InnoDB;

CREATE TABLE unidad_medida (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo                 VARCHAR(20)   NOT NULL UNIQUE,
    nombre                 VARCHAR(80)   NOT NULL,
    factor_conversion_base DECIMAL(18,6) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- =============================================================
-- PROVEEDORES (antes de usuario: el usuario puede pertenecer a uno)
-- =============================================================
CREATE TABLE proveedor (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    razon_social    VARCHAR(200) NOT NULL,
    nit             VARCHAR(30)  NOT NULL UNIQUE,
    correo_contacto VARCHAR(150),
    telefono        VARCHAR(30),
    direccion       VARCHAR(250),
    latitud         DECIMAL(10,7),
    longitud        DECIMAL(10,7),
    score_actual    DECIMAL(5,2) NULL,          -- rollup de evaluacion_proveedor
    activo          TINYINT(1)   NOT NULL DEFAULT 1,
    creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- [C3] categorías que cada proveedor puede cotizar
CREATE TABLE proveedor_categoria (
    proveedor_id          BIGINT NOT NULL,
    categoria_producto_id BIGINT NOT NULL,
    PRIMARY KEY (proveedor_id, categoria_producto_id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    FOREIGN KEY (categoria_producto_id) REFERENCES categoria_producto(id)
) ENGINE=InnoDB;

-- =============================================================
-- SEGURIDAD
-- =============================================================
CREATE TABLE rol (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL UNIQUE,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(250)
) ENGINE=InnoDB;

CREATE TABLE permiso (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(80)  NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE rol_permiso (
    rol_id     BIGINT NOT NULL,
    permiso_id BIGINT NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    FOREIGN KEY (rol_id) REFERENCES rol(id),
    FOREIGN KEY (permiso_id) REFERENCES permiso(id)
) ENGINE=InnoDB;

-- [C1][C2] proveedor_id solo se llena para usuarios del portal proveedor
CREATE TABLE usuario (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id      BIGINT       NOT NULL,
    centro_costo_id BIGINT       NULL,
    proveedor_id    BIGINT       NULL,
    nombre_completo VARCHAR(200) NOT NULL,
    correo          VARCHAR(150) NOT NULL UNIQUE,
    hash_password   VARCHAR(255) NOT NULL,
    activo          TINYINT(1)   NOT NULL DEFAULT 1,
    ultimo_acceso   DATETIME     NULL,
    creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (centro_costo_id) REFERENCES centro_costo(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

CREATE TABLE usuario_rol (
    usuario_id BIGINT NOT NULL,
    rol_id     BIGINT NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (rol_id) REFERENCES rol(id)
) ENGINE=InnoDB;

-- =============================================================
-- POLÍTICAS Y PESOS DE EVALUACIÓN
-- =============================================================
-- [C4] quién aprueba qué rango de monto
CREATE TABLE politica_aprobacion (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id       BIGINT        NOT NULL,
    tipo             ENUM('SOLICITUD','ADJUDICACION') NOT NULL,
    rol_aprobador_id BIGINT        NOT NULL,
    monto_desde      DECIMAL(18,2) NOT NULL,
    monto_hasta      DECIMAL(18,2) NULL,        -- NULL = sin tope
    activo           TINYINT(1)    NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (rol_aprobador_id) REFERENCES rol(id)
) ENGINE=InnoDB;

CREATE TABLE criterio_evaluacion (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40)  NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- matriz de pesos versionada (snapshot por análisis)
CREATE TABLE config_pesos_evaluacion (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id    BIGINT     NOT NULL,
    vigente_desde DATETIME   NOT NULL,
    vigente_hasta DATETIME   NULL,
    activo        TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- la app valida que SUM(peso) = 100 por config
CREATE TABLE config_pesos_evaluacion_detalle (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_pesos_evaluacion_id BIGINT       NOT NULL,
    criterio_evaluacion_id     BIGINT       NOT NULL,
    peso                       DECIMAL(5,2) NOT NULL,
    UNIQUE KEY uq_config_criterio (config_pesos_evaluacion_id, criterio_evaluacion_id),
    FOREIGN KEY (config_pesos_evaluacion_id) REFERENCES config_pesos_evaluacion(id),
    FOREIGN KEY (criterio_evaluacion_id) REFERENCES criterio_evaluacion(id)
) ENGINE=InnoDB;

-- =============================================================
-- PRODUCTOS E INVENTARIO
-- =============================================================
CREATE TABLE producto (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    categoria_producto_id BIGINT        NOT NULL,
    unidad_medida_id      BIGINT        NOT NULL,
    codigo                VARCHAR(50)   NOT NULL UNIQUE,
    nombre                VARCHAR(200)  NOT NULL,
    descripcion           VARCHAR(500),
    stock_minimo          DECIMAL(18,4) NOT NULL DEFAULT 0,
    stock_maximo          DECIMAL(18,4) NULL,
    costo_referencia      DECIMAL(18,4) NULL,
    activo                TINYINT(1)    NOT NULL DEFAULT 1,
    FOREIGN KEY (categoria_producto_id) REFERENCES categoria_producto(id),
    FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;

CREATE TABLE bodega (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT       NOT NULL,
    sede_id    BIGINT       NULL,
    nombre     VARCHAR(150) NOT NULL,
    direccion  VARCHAR(250),
    activo     TINYINT(1)   NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (sede_id) REFERENCES sede(id)
) ENGINE=InnoDB;

-- fuente de verdad del stock; solo la modifica movimiento_inventario
CREATE TABLE stock_producto_bodega (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT        NOT NULL,
    bodega_id   BIGINT        NOT NULL,
    cantidad    DECIMAL(18,4) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_producto_bodega (producto_id, bodega_id),
    CHECK (cantidad >= 0),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

CREATE TABLE movimiento_inventario (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id       BIGINT        NOT NULL,
    bodega_origen_id  BIGINT        NULL,
    bodega_destino_id BIGINT        NULL,
    usuario_id        BIGINT        NOT NULL,
    tipo              ENUM('ENTRADA_COMPRA','SALIDA_CONSUMO','AJUSTE_POSITIVO',
                           'AJUSTE_NEGATIVO','DEVOLUCION_PROVEEDOR','TRASLADO') NOT NULL,
    cantidad          DECIMAL(18,4) NOT NULL,
    stock_resultante  DECIMAL(18,4) NOT NULL,
    referencia_tipo   VARCHAR(50)   NULL,       -- p. ej. 'RECEPCION'
    referencia_id     BIGINT        NULL,
    motivo            VARCHAR(250)  NULL,       -- obligatorio en ajustes (app)
    creado_en         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (cantidad > 0),
    INDEX idx_mov_producto (producto_id, creado_en),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (bodega_origen_id) REFERENCES bodega(id),
    FOREIGN KEY (bodega_destino_id) REFERENCES bodega(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =============================================================
-- SOLICITUDES DE COMPRA
-- =============================================================
-- [C5] se agrega DEVUELTA
CREATE TABLE solicitud_compra (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id             BIGINT       NOT NULL,
    centro_costo_id        BIGINT       NOT NULL,
    usuario_solicitante_id BIGINT       NOT NULL,
    consecutivo            VARCHAR(30)  NOT NULL UNIQUE,
    motivo                 VARCHAR(500),
    fecha_requerida        DATE         NOT NULL,
    estado                 ENUM('BORRADOR','PENDIENTE_APROBACION','APROBADA','DEVUELTA',
                                'RECHAZADA','EN_RFQ','ADJUDICADA','CANCELADA','CERRADA')
                           NOT NULL DEFAULT 'BORRADOR',
    creado_en              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    FOREIGN KEY (centro_costo_id) REFERENCES centro_costo(id),
    FOREIGN KEY (usuario_solicitante_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE solicitud_compra_linea (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id BIGINT        NOT NULL,
    producto_id         BIGINT        NOT NULL,
    unidad_medida_id    BIGINT        NOT NULL,
    cantidad            DECIMAL(18,4) NOT NULL,
    CHECK (cantidad > 0),
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;

CREATE TABLE aprobacion_solicitud (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id    BIGINT   NOT NULL,
    usuario_aprobador_id   BIGINT   NOT NULL,
    politica_aprobacion_id BIGINT   NULL,
    decision               ENUM('APROBADA','RECHAZADA','DEVUELTA') NOT NULL,
    observaciones          VARCHAR(500),
    decidido_en            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (usuario_aprobador_id) REFERENCES usuario(id),
    FOREIGN KEY (politica_aprobacion_id) REFERENCES politica_aprobacion(id)
) ENGINE=InnoDB;

-- =============================================================
-- RFQ Y COTIZACIONES
-- =============================================================
CREATE TABLE rfq (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id BIGINT        NOT NULL,
    usuario_id          BIGINT        NOT NULL,
    consecutivo         VARCHAR(30)   NOT NULL UNIQUE,
    fecha_limite        DATETIME      NOT NULL,
    condiciones         VARCHAR(1000),
    estado              ENUM('BORRADOR','ABIERTA','CERRADA','ADJUDICADA','CANCELADA')
                        NOT NULL DEFAULT 'BORRADOR',
    creado_en           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE rfq_linea (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id           BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    unidad_medida_id BIGINT        NOT NULL,
    cantidad         DECIMAL(18,4) NOT NULL,
    especificaciones VARCHAR(1000),
    CHECK (cantidad > 0),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (unidad_medida_id) REFERENCES unidad_medida(id)
) ENGINE=InnoDB;

CREATE TABLE rfq_proveedor (
    rfq_id       BIGINT   NOT NULL,
    proveedor_id BIGINT   NOT NULL,
    invitado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rfq_id, proveedor_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

-- [C6] datos que usa el score (entrega, garantía) y totales del backend
CREATE TABLE cotizacion (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id                  BIGINT        NOT NULL,
    proveedor_id            BIGINT        NOT NULL,
    estado                  ENUM('BORRADOR','ENVIADA','EN_ANALISIS','SELECCIONADA',
                                 'NO_SELECCIONADA','RECHAZADA') NOT NULL DEFAULT 'BORRADOR',
    origen                  ENUM('FORMULARIO','ARCHIVO') NOT NULL,
    moneda                  CHAR(3)       NOT NULL DEFAULT 'COP',
    plazo_entrega_dias      INT           NULL,
    garantia_meses          INT           NULL,
    validez_hasta           DATE          NULL,
    condiciones_comerciales VARCHAR(1000),
    subtotal                DECIMAL(18,2) NULL,   -- calculado en Java
    impuestos               DECIMAL(18,2) NULL,
    total                   DECIMAL(18,2) NULL,
    documento_original_id   BIGINT        NULL,   -- FK a documento (ALTER al final)
    enviado_en              DATETIME      NULL,
    creado_en               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cotizacion_rfq_proveedor (rfq_id, proveedor_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

-- [C6] normalización: precio por unidad base = precio / factor_conversion
CREATE TABLE cotizacion_linea (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY,
    cotizacion_id               BIGINT        NOT NULL,
    rfq_linea_id                BIGINT        NOT NULL,
    producto_id                 BIGINT        NOT NULL,
    cantidad_ofertada           DECIMAL(18,4) NOT NULL,
    unidad_ofertada             VARCHAR(50)   NOT NULL,          -- texto tal cual ("caja x24")
    factor_conversion           DECIMAL(18,6) NOT NULL DEFAULT 1, -- unidades base por unidad ofertada
    precio_unitario_ofertado    DECIMAL(18,4) NOT NULL,
    descuento_pct               DECIMAL(5,2)  NOT NULL DEFAULT 0,
    impuesto_pct                DECIMAL(5,2)  NOT NULL DEFAULT 0,
    precio_unitario_normalizado DECIMAL(18,4) NULL,              -- calculado en Java
    subtotal                    DECIMAL(18,2) NULL,              -- calculado en Java
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id),
    FOREIGN KEY (rfq_linea_id) REFERENCES rfq_linea(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- =============================================================
-- IA, ANÁLISIS Y ADJUDICACIÓN
-- =============================================================
-- [C7] job asíncrono por cotización cargada como archivo
CREATE TABLE procesamiento_ia (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    cotizacion_id  BIGINT       NOT NULL,
    estado         ENUM('PENDIENTE','PROCESANDO','COMPLETADO',
                        'REQUIERE_REVISION','ERROR') NOT NULL DEFAULT 'PENDIENTE',
    modelo         VARCHAR(80)  NULL,
    intentos       INT          NOT NULL DEFAULT 0,
    respuesta_json JSON         NULL,      -- respuesta cruda validada contra el esquema
    error          VARCHAR(1000) NULL,
    creado_en      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizado_en  DATETIME     NULL,
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id)
) ENGINE=InnoDB;

-- [C7] confianza por dato y confirmación humana si es baja
CREATE TABLE extraccion_campo (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    procesamiento_ia_id       BIGINT       NOT NULL,
    cotizacion_linea_id       BIGINT       NULL,    -- NULL = campo de cabecera
    campo                     VARCHAR(50)  NOT NULL, -- 'precio','cantidad','unidad',...
    valor_extraido            VARCHAR(500) NULL,
    confianza                 DECIMAL(4,3) NOT NULL, -- 0.000 - 1.000
    es_critico                TINYINT(1)   NOT NULL DEFAULT 0,
    valor_confirmado          VARCHAR(500) NULL,
    confirmado_por_usuario_id BIGINT       NULL,
    confirmado_en             DATETIME     NULL,
    FOREIGN KEY (procesamiento_ia_id) REFERENCES procesamiento_ia(id),
    FOREIGN KEY (cotizacion_linea_id) REFERENCES cotizacion_linea(id),
    FOREIGN KEY (confirmado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- [C8] estado y publicación del ranking
CREATE TABLE analisis_cotizacion (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id                     BIGINT   NOT NULL,
    config_pesos_evaluacion_id BIGINT   NOT NULL,   -- snapshot de pesos usado
    usuario_id                 BIGINT   NOT NULL,
    estado                     ENUM('BORRADOR','PUBLICADO') NOT NULL DEFAULT 'BORRADOR',
    creado_en                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_en               DATETIME NULL,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (config_pesos_evaluacion_id) REFERENCES config_pesos_evaluacion(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE analisis_cotizacion_resultado (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    analisis_cotizacion_id BIGINT        NOT NULL,
    cotizacion_id          BIGINT        NOT NULL,
    costo_total            DECIMAL(18,2) NOT NULL,
    score_final            DECIMAL(5,2)  NOT NULL,
    posicion_ranking       SMALLINT      NOT NULL,
    explicacion_ia         TEXT          NULL,     -- [C8] ventajas/desventajas
    UNIQUE KEY uq_analisis_cotizacion (analisis_cotizacion_id, cotizacion_id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id)
) ENGINE=InnoDB;

CREATE TABLE analisis_cotizacion_detalle (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    analisis_cotizacion_id BIGINT       NOT NULL,
    cotizacion_id          BIGINT       NOT NULL,
    criterio_evaluacion_id BIGINT       NOT NULL,
    peso_aplicado          DECIMAL(5,2) NOT NULL,   -- copiado del snapshot
    puntaje                DECIMAL(5,2) NOT NULL,   -- 0-100
    score_ponderado        DECIMAL(6,2) NOT NULL,   -- puntaje * peso / 100
    UNIQUE KEY uq_analisis_cot_criterio (analisis_cotizacion_id, cotizacion_id, criterio_evaluacion_id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_id) REFERENCES cotizacion(id),
    FOREIGN KEY (criterio_evaluacion_id) REFERENCES criterio_evaluacion(id)
) ENGINE=InnoDB;

-- [C8] alertas (precio atípico, etc.); nunca excluyen la oferta
CREATE TABLE anomalia_cotizacion (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    analisis_resultado_id BIGINT       NOT NULL,
    tipo                  VARCHAR(50)  NOT NULL,   -- 'PRECIO_ATIPICO', ...
    severidad             ENUM('BAJA','MEDIA','ALTA') NOT NULL,
    descripcion           VARCHAR(500) NOT NULL,
    FOREIGN KEY (analisis_resultado_id) REFERENCES analisis_cotizacion_resultado(id)
) ENGINE=InnoDB;

-- [C9] estado y aprobación de la adjudicación
CREATE TABLE adjudicacion (
    id                         BIGINT AUTO_INCREMENT PRIMARY KEY,
    rfq_id                     BIGINT        NOT NULL,
    analisis_cotizacion_id     BIGINT        NOT NULL,
    cotizacion_seleccionada_id BIGINT        NOT NULL,
    usuario_id                 BIGINT        NOT NULL,
    monto_total                DECIMAL(18,2) NOT NULL,
    sigue_recomendacion        TINYINT(1)    NOT NULL,
    justificacion              VARCHAR(1000) NULL,   -- obligatoria si sigue_recomendacion = 0
    estado                     ENUM('PENDIENTE_APROBACION','APROBADA','RECHAZADA')
                               NOT NULL DEFAULT 'PENDIENTE_APROBACION',
    aprobado_por_usuario_id    BIGINT        NULL,
    aprobado_en                DATETIME      NULL,
    observaciones_aprobacion   VARCHAR(500)  NULL,
    decidido_en                DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (analisis_cotizacion_id) REFERENCES analisis_cotizacion(id),
    FOREIGN KEY (cotizacion_seleccionada_id) REFERENCES cotizacion(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (aprobado_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- =============================================================
-- ÓRDENES, PAGO Y RECEPCIÓN
-- =============================================================
-- [C10] cada cambio crea una fila nueva con orden_padre_id
CREATE TABLE orden_compra (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY,
    adjudicacion_id        BIGINT        NOT NULL,
    proveedor_id           BIGINT        NOT NULL,
    bodega_destino_id      BIGINT        NOT NULL,
    consecutivo            VARCHAR(30)   NOT NULL,
    version                INT           NOT NULL DEFAULT 1,
    orden_padre_id         BIGINT        NULL,
    motivo_version         VARCHAR(500)  NULL,
    total                  DECIMAL(18,2) NOT NULL,
    lugar_entrega          VARCHAR(250)  NOT NULL,
    fecha_entrega_esperada DATE          NOT NULL,  -- base para puntualidad
    estado                 ENUM('EMITIDA','ACEPTADA_PROVEEDOR','RECHAZADA_PROVEEDOR',
                                'EN_PREPARACION','DESPACHADA','RECIBIDA_PARCIAL',
                                'RECIBIDA_TOTAL','CERRADA','CANCELADA','REEMPLAZADA')
                           NOT NULL DEFAULT 'EMITIDA',
    creado_en              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_orden_version (consecutivo, version),
    FOREIGN KEY (adjudicacion_id) REFERENCES adjudicacion(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    FOREIGN KEY (bodega_destino_id) REFERENCES bodega(id),
    FOREIGN KEY (orden_padre_id) REFERENCES orden_compra(id)
) ENGINE=InnoDB;

CREATE TABLE orden_compra_linea (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id     BIGINT        NOT NULL,
    cotizacion_linea_id BIGINT        NULL,
    producto_id         BIGINT        NOT NULL,
    cantidad            DECIMAL(18,4) NOT NULL,
    precio_unitario     DECIMAL(18,4) NOT NULL,
    subtotal            DECIMAL(18,2) NOT NULL,
    CHECK (cantidad > 0),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (cotizacion_linea_id) REFERENCES cotizacion_linea(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

CREATE TABLE orden_compra_estado_historial (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT       NOT NULL,
    estado          VARCHAR(30)  NOT NULL,
    usuario_id      BIGINT       NOT NULL,
    observacion     VARCHAR(500) NULL,
    creado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- [C11] evidencia_pendiente cuando se registra sin comprobante
CREATE TABLE pago (
    id                        BIGINT AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id           BIGINT        NOT NULL,
    usuario_id                BIGINT        NOT NULL,
    metodo                    VARCHAR(50)   NOT NULL,
    valor                     DECIMAL(18,2) NOT NULL,
    fecha_pago                DATE          NOT NULL,
    referencia                VARCHAR(100),
    estado                    ENUM('PENDIENTE','REGISTRADO') NOT NULL DEFAULT 'PENDIENTE',
    evidencia_pendiente       TINYINT(1)    NOT NULL DEFAULT 0,
    comprobante_documento_id  BIGINT        NULL,   -- FK a documento (ALTER al final)
    creado_en                 DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (valor > 0),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE recepcion (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT       NOT NULL,
    usuario_id      BIGINT       NOT NULL,
    bodega_id       BIGINT       NOT NULL,
    recibido_en     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones   VARCHAR(500),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

-- [C12] EN_REVISION cuando llega más de lo pedido (no suma stock)
CREATE TABLE recepcion_linea (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    recepcion_id          BIGINT        NOT NULL,
    orden_compra_linea_id BIGINT        NOT NULL,
    cantidad_recibida     DECIMAL(18,4) NOT NULL,
    cantidad_faltante     DECIMAL(18,4) NOT NULL DEFAULT 0,
    cantidad_rechazada    DECIMAL(18,4) NOT NULL DEFAULT 0,
    estado                ENUM('ACEPTADA','EN_REVISION') NOT NULL DEFAULT 'ACEPTADA',
    observacion_calidad   VARCHAR(500)  NULL,
    FOREIGN KEY (recepcion_id) REFERENCES recepcion(id),
    FOREIGN KEY (orden_compra_linea_id) REFERENCES orden_compra_linea(id)
) ENGINE=InnoDB;

-- =============================================================
-- EVALUACIÓN DE PROVEEDORES
-- =============================================================
-- un registro por orden; NULL = indicador sin datos suficientes (UC-14)
CREATE TABLE evaluacion_proveedor (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    proveedor_id          BIGINT       NOT NULL,
    orden_compra_id       BIGINT       NOT NULL,
    puntualidad           DECIMAL(5,2) NULL,
    cumplimiento_cantidad DECIMAL(5,2) NULL,
    incidencias           DECIMAL(5,2) NULL,
    calidad               DECIMAL(5,2) NULL,
    precio                DECIMAL(5,2) NULL,
    respuesta             DECIMAL(5,2) NULL,
    score                 DECIMAL(5,2) NULL,
    creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_eval_orden (proveedor_id, orden_compra_id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id),
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id)
) ENGINE=InnoDB;

-- =============================================================
-- DOCUMENTOS, NOTIFICACIONES Y AUDITORÍA
-- =============================================================
CREATE TABLE documento (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo        VARCHAR(250) NOT NULL,
    ruta_storage          VARCHAR(500) NOT NULL,
    tipo_mime             VARCHAR(100) NOT NULL,
    tamano_bytes          BIGINT       NOT NULL,
    entidad_tipo          VARCHAR(50)  NOT NULL,  -- 'COTIZACION','ORDEN_COMPRA','RECEPCION','PAGO','PROVEEDOR'
    entidad_id            BIGINT       NOT NULL,
    subido_por_usuario_id BIGINT       NULL,
    creado_en             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_documento_entidad (entidad_tipo, entidad_id),
    FOREIGN KEY (subido_por_usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE config_notificacion (
    id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    evento VARCHAR(80) NOT NULL,
    canal  ENUM('EMAIL','INTERNA') NOT NULL,
    activo TINYINT(1)  NOT NULL DEFAULT 1,
    UNIQUE KEY uq_evento_canal (evento, canal)
) ENGINE=InnoDB;

CREATE TABLE notificacion (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT       NOT NULL,
    evento     VARCHAR(80)  NOT NULL,
    mensaje    VARCHAR(500) NOT NULL,
    leida      TINYINT(1)   NOT NULL DEFAULT 0,
    creado_en  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notif_usuario (usuario_id, leida),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

CREATE TABLE auditoria (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT      NULL,
    entidad    VARCHAR(80) NOT NULL,
    entidad_id BIGINT      NULL,
    accion     VARCHAR(80) NOT NULL,
    resultado  ENUM('EXITO','FALLO') NOT NULL DEFAULT 'EXITO',
    ip         VARCHAR(45) NULL,
    detalle    TEXT        NULL,
    creado_en  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_auditoria_entidad (entidad, entidad_id),
    INDEX idx_auditoria_fecha (creado_en),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- FKs hacia documento (se crea al final por dependencia circular)
ALTER TABLE cotizacion ADD CONSTRAINT fk_cotizacion_documento
    FOREIGN KEY (documento_original_id) REFERENCES documento(id);
ALTER TABLE pago ADD CONSTRAINT fk_pago_documento
    FOREIGN KEY (comprobante_documento_id) REFERENCES documento(id);
