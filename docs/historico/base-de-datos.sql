-- =============================================================
-- SUPPLY-CORE — Base de datos completa
-- Sistema de Abastecimiento (Inventario + Compras + Proveedores
-- + RFQ/Cotizaciones + IA + Adjudicación + Órdenes + Pago +
-- Recepción)
-- Motor: MySQL 8.0.16 o superior (requerido para que las
-- restricciones CHECK se validen de verdad).
-- =============================================================

-- -------------------------------------------------------------
-- CREACIÓN DE LA BASE DE DATOS
-- -------------------------------------------------------------
-- utf8mb4: permite guardar cualquier carácter (tildes, ñ, símbolos,
--          emojis) sin errores de codificación.
-- unicode_ci: compara y ordena texto sin distinguir mayúsculas de
--          minúsculas, y trata los acentos como corresponde en
--          español (á se ordena junto a a).
CREATE DATABASE IF NOT EXISTS supply_core
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE supply_core;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================
-- NÚCLEO Y SEGURIDAD
-- Quién existe en el sistema, a qué empresa/sede pertenece y qué
-- puede hacer (roles y permisos). Todo lo demás en la base
-- depende, directa o indirectamente, de este bloque.
-- =============================================================

-- Empresa dueña de la operación. Es la raíz de todo: sedes,
-- usuarios, bodegas y políticas de aprobación cuelgan de ella.
CREATE TABLE empresa (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    razon_social      VARCHAR(200)    NOT NULL,
    nit               VARCHAR(30)     NOT NULL UNIQUE,
    direccion_principal VARCHAR(250),
    moneda_base       CHAR(3)         NOT NULL DEFAULT 'COP',
    creado_en         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- Sedes físicas de la empresa (oficinas, plantas). Una empresa
-- puede tener varias sedes (1:N con empresa).
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

-- Catálogo de roles (ej. Comprador, Aprobador, Almacenista).
-- No pertenece a una empresa: es un catálogo general del sistema.
CREATE TABLE rol (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL UNIQUE,
    nombre      VARCHAR(100) NOT NULL,
    descripcion VARCHAR(250)
) ENGINE=InnoDB;

-- Catálogo de permisos finos (ej. "aprobar_solicitud",
-- "emitir_orden"). Un rol agrupa varios permisos.
CREATE TABLE permiso (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(80)  NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL
) ENGINE=InnoDB;

-- Relación N:M entre rol y permiso: un rol tiene muchos permisos,
-- y un mismo permiso puede pertenecer a varios roles.
CREATE TABLE rol_permiso (
    rol_id     BIGINT UNSIGNED NOT NULL,
    permiso_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    FOREIGN KEY (rol_id) REFERENCES rol(id),
    FOREIGN KEY (permiso_id) REFERENCES permiso(id)
) ENGINE=InnoDB;

-- Personas que inician sesión en el sistema (no proveedores).
-- Pertenecen a una empresa (N:1).
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

-- Relación N:M real entre usuario y rol: un usuario puede tener
-- más de un rol (ej. Comprador y Aprobador a la vez).
CREATE TABLE usuario_rol (
    usuario_id BIGINT UNSIGNED NOT NULL,
    rol_id     BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    FOREIGN KEY (usuario_id) REFERENCES usuario(id),
    FOREIGN KEY (rol_id) REFERENCES rol(id)
) ENGINE=InnoDB;

-- =============================================================
-- M02: CONFIGURACIÓN Y MAESTROS
-- Catálogos generales que usan casi todos los demás módulos:
-- centros de costo, categorías, unidades, políticas de aprobación
-- y los criterios/pesos que usa la IA para comparar cotizaciones.
-- =============================================================

-- Centro de costo al que se imputa una solicitud de compra.
CREATE TABLE centro_costo (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30)  NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- Categoría de producto (ej. "Materia prima", "Empaques").
CREATE TABLE categoria_producto (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL
) ENGINE=InnoDB;

-- Unidad de medida (ej. "Caja x24", "Kilogramo"). factor_conversion_base
-- permite convertir cualquier unidad a una unidad base comparable,
-- clave para que la IA compare ofertas en distintas presentaciones.
CREATE TABLE unidad_medida (
    id                     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo                 VARCHAR(20)  NOT NULL UNIQUE,
    nombre                 VARCHAR(80)  NOT NULL,
    factor_conversion_base DECIMAL(18,6) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- Reglas de aprobación por rango de monto (ej. "de $0 a $5M no
-- requiere aprobación adicional"). Pertenece a una empresa.
CREATE TABLE politica_aprobacion (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id   BIGINT UNSIGNED NOT NULL,
    monto_desde  DECIMAL(18,2) NOT NULL,
    monto_hasta  DECIMAL(18,2) NOT NULL,
    activo       TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- Catálogo de criterios que la IA usa para comparar cotizaciones
-- (precio, calidad, entrega, garantía, historial, riesgo,
-- condiciones). Normalizado en tabla en vez de columnas fijas,
-- para poder agregar o quitar criterios sin tocar el esquema.
CREATE TABLE criterio_evaluacion (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(40)  NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- Cabecera de una "versión" de la matriz de pesos de evaluación.
-- Versionada con vigente_desde/vigente_hasta para que, si los
-- pesos cambian mañana, los análisis históricos no se alteren.
CREATE TABLE config_pesos_evaluacion (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id     BIGINT UNSIGNED NOT NULL,
    vigente_desde  DATETIME NOT NULL,
    vigente_hasta  DATETIME NULL,
    activo         TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- Detalle: cuánto pesa (%) cada criterio dentro de una versión de
-- la matriz. La suma de los pesos de una misma config debe dar
-- 100 (esa regla se valida en el backend, no aquí).
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
-- Qué se compra/almacena, en qué bodega, cuánto hay y por qué
-- cambió esa cantidad. El stock nunca se edita a mano: siempre
-- es consecuencia de un movimiento_inventario.
-- =============================================================

-- Catálogo de productos. Cada producto pertenece a una categoría
-- y se mide en una unidad de medida.
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

-- Bodega o ubicación física donde se guarda inventario. Una
-- empresa puede tener varias bodegas.
CREATE TABLE bodega (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT UNSIGNED NOT NULL,
    nombre     VARCHAR(150) NOT NULL,
    direccion  VARCHAR(250),
    activo     TINYINT(1) NOT NULL DEFAULT 1,
    FOREIGN KEY (empresa_id) REFERENCES empresa(id)
) ENGINE=InnoDB;

-- Existencia real: cuánto hay de cada producto, en cada bodega.
-- Esta es la ÚNICA fuente de verdad del stock; se actualiza
-- exclusivamente desde movimiento_inventario, nunca con un UPDATE
-- manual directo desde una pantalla.
CREATE TABLE stock_producto_bodega (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT UNSIGNED NOT NULL,
    bodega_id   BIGINT UNSIGNED NOT NULL,
    cantidad    DECIMAL(18,4) NOT NULL DEFAULT 0,
    UNIQUE KEY uq_producto_bodega (producto_id, bodega_id),
    FOREIGN KEY (producto_id) REFERENCES producto(id),
    FOREIGN KEY (bodega_id) REFERENCES bodega(id)
) ENGINE=InnoDB;

-- Historial de todo movimiento que afecta el stock: entradas,
-- salidas, ajustes, devoluciones y traslados entre bodegas
-- (bodega_origen_id / bodega_destino_id). stock_resultante deja
-- registrado el saldo justo después del movimiento, para auditoría.
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
-- Quién nos vende. Tiene su propio login (hash_password) porque
-- el proveedor entra a su propio portal, no al sistema interno.
-- =============================================================

-- score_actual es el rollup (resumen) del desempeño histórico del
-- proveedor, alimentado por evaluacion_proveedor más adelante.
CREATE TABLE proveedor (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    razon_social   VARCHAR(200) NOT NULL,
    nit            VARCHAR(30)  NOT NULL UNIQUE,
    correo_contacto VARCHAR(150),
    hash_password  VARCHAR(255) NULL,          -- login propio del portal
    telefono       VARCHAR(30),
    latitud        DECIMAL(10,7),
    longitud       DECIMAL(10,7),
    score_actual   DECIMAL(5,2) NULL,          -- rollup del score histórico
    activo         TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- =============================================================
-- M05: SOLICITUDES DE COMPRA
-- El punto de partida del ciclo: alguien necesita algo, se
-- registra la necesidad y se aprueba (o no) antes de cotizar.
-- =============================================================

-- Encabezado de la solicitud: qué se necesita, quién lo pide, a
-- qué centro de costo se imputa y en qué estado va.
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

-- Detalle línea por línea: qué producto y cuánto se solicita.
-- Una solicitud puede tener varias líneas (1:N).
CREATE TABLE solicitud_compra_linea (
    id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    solicitud_compra_id BIGINT UNSIGNED NOT NULL,
    producto_id        BIGINT UNSIGNED NOT NULL,
    cantidad           DECIMAL(18,4) NOT NULL,
    CHECK (cantidad > 0),
    FOREIGN KEY (solicitud_compra_id) REFERENCES solicitud_compra(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- Registro de la decisión (aprobar/rechazar/devolver) sobre una
-- solicitud, con qué política de aprobación se aplicó y quién
-- decidió. Es el rastro de auditoría de esa decisión puntual.
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
-- Con la solicitud aprobada, se pide cotización a varios
-- proveedores (RFQ) y cada uno responde con su propia cotización.
-- =============================================================

-- Encabezado de la solicitud de cotización (RFQ), generada a
-- partir de una solicitud_compra ya aprobada.
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

-- Detalle línea por línea de qué se pide cotizar en esa RFQ.
CREATE TABLE rfq_linea (
    id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id         BIGINT UNSIGNED NOT NULL,
    producto_id    BIGINT UNSIGNED NOT NULL,
    cantidad       DECIMAL(18,4) NOT NULL,
    especificaciones VARCHAR(1000),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (producto_id) REFERENCES producto(id)
) ENGINE=InnoDB;

-- Relación N:M: qué proveedores fueron invitados a cotizar en
-- cada RFQ (una RFQ invita a varios proveedores).
CREATE TABLE rfq_proveedor (
    rfq_id       BIGINT UNSIGNED NOT NULL,
    proveedor_id BIGINT UNSIGNED NOT NULL,
    invitado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (rfq_id, proveedor_id),
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

-- Encabezado de la cotización que un proveedor envía en respuesta
-- a una RFQ. documento_original_id conserva el archivo tal cual
-- llegó (Excel/PDF/imagen), sin importar lo que la IA extraiga.
CREATE TABLE cotizacion (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id               BIGINT UNSIGNED NOT NULL,
    proveedor_id         BIGINT UNSIGNED NOT NULL,
    estado               ENUM('BORRADOR','ENVIADA','EN_ANALISIS','SELECCIONADA',
                              'NO_SELECCIONADA','RECHAZADA') NOT NULL DEFAULT 'BORRADOR',
    condiciones_comerciales VARCHAR(1000),
    documento_original_id BIGINT UNSIGNED NULL,   -- FK a documento (se agrega al final del script)
    creado_en            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (proveedor_id) REFERENCES proveedor(id)
) ENGINE=InnoDB;

-- Detalle línea por línea de la oferta: precio, unidad ofertada y
-- descuento. confianza_extraccion es el dato que entrega la IA
-- indicando qué tan segura está de haber leído bien ese campo.
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
-- Aquí vive el diferencial del proyecto: la IA compara las
-- cotizaciones de una misma RFQ y arma un ranking explicado. La
-- IA nunca decide sola: el resultado queda disponible para que
-- una persona adjudique.
-- =============================================================

-- Cabecera de un análisis de IA para una RFQ. Guarda a qué
-- versión de config_pesos_evaluacion corresponde (snapshot),
-- así los pesos cambien después, este análisis no se altera.
CREATE TABLE analisis_cotizacion (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    rfq_id                      BIGINT UNSIGNED NOT NULL,
    config_pesos_evaluacion_id  BIGINT UNSIGNED NOT NULL, -- snapshot usado
    creado_en                   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (rfq_id) REFERENCES rfq(id),
    FOREIGN KEY (config_pesos_evaluacion_id) REFERENCES config_pesos_evaluacion(id)
) ENGINE=InnoDB;

-- Resultado agregado por cotización: el score final y la
-- posición en el ranking (1° = mejor alternativa). Esto es lo
-- que se muestra directamente en pantalla.
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

-- Detalle: puntaje que sacó cada cotización en cada criterio
-- (precio, calidad, entrega...), con el peso que se le aplicó.
-- Es lo que permite explicar "por qué" quedó primera una oferta.
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

-- La decisión final: qué cotización se eligió, quién decidió, y
-- si siguió o no la recomendación de la IA (con justificación
-- obligatoria a nivel de aplicación si se apartó de ella).
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
-- Con la adjudicación lista, se formaliza la compra (orden), se
-- registra el pago y se recibe la mercancía (lo único que
-- realmente mueve el inventario).
-- =============================================================

-- Orden de compra formal. version se incrementa si hay que
-- modificarla después de emitida, en vez de sobrescribirla, para
-- no perder el historial de qué se acordó originalmente.
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

-- Detalle línea por línea de la orden: qué producto, cuánto y a
-- qué precio quedó pactado.
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

-- Registro del pago de una orden (no es una pasarela de pagos,
-- solo queda constancia de que se pagó, cómo y cuándo).
CREATE TABLE pago (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    orden_compra_id BIGINT UNSIGNED NOT NULL,
    metodo          VARCHAR(50) NOT NULL,
    valor           DECIMAL(18,2) NOT NULL,
    fecha_pago      DATE NOT NULL,
    referencia      VARCHAR(100),
    estado          ENUM('PENDIENTE','REGISTRADO') NOT NULL DEFAULT 'PENDIENTE',
    comprobante_documento_id BIGINT UNSIGNED NULL, -- FK a documento (se agrega al final del script)
    FOREIGN KEY (orden_compra_id) REFERENCES orden_compra(id)
) ENGINE=InnoDB;

-- Encabezado de la recepción de mercancía en una bodega. Es el
-- ÚNICO evento que genera un movimiento_inventario de tipo
-- entrada por compra: una orden emitida, por sí sola, nunca
-- mueve inventario.
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

-- Detalle línea por línea de lo efectivamente recibido, con
-- diferencias (faltante, rechazado) frente a lo pedido en
-- orden_compra_linea.
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
-- Qué tan bien se comportó el proveedor en una orden puntual.
-- Estos registros alimentan proveedor.score_actual.
-- =============================================================

-- Un registro de evaluación por cada orden de compra cerrada con
-- un proveedor, con los indicadores de desempeño de esa entrega.
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
-- Módulos transversales: qué archivos se subieron, qué avisos se
-- enviaron y quién hizo qué y cuándo dentro del sistema.
-- =============================================================

-- Cualquier archivo subido al sistema (cotización, comprobante de
-- pago, evidencia de recepción, etc.). entidad_tipo + entidad_id
-- son una FK polimórfica: le dicen a qué registro pertenece ese
-- archivo sin necesidad de una tabla de documentos por módulo.
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

-- Plantillas de notificación: qué evento dispara aviso y por qué
-- canal (correo, notificación interna).
CREATE TABLE config_notificacion (
    id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    evento VARCHAR(80) NOT NULL,
    canal  VARCHAR(30) NOT NULL, -- EMAIL, INTERNA
    activo TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB;

-- Notificaciones concretas que un usuario recibió (instancias de
-- las plantillas de config_notificacion).
CREATE TABLE notificacion (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT UNSIGNED NOT NULL,
    evento     VARCHAR(80) NOT NULL,
    mensaje    VARCHAR(500) NOT NULL,
    leida      TINYINT(1) NOT NULL DEFAULT 0,
    creado_en  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (usuario_id) REFERENCES usuario(id)
) ENGINE=InnoDB;

-- Bitácora general: quién hizo qué acción sobre qué registro y
-- cuándo. entidad + entidad_id identifican el registro afectado
-- (igual que en documento, es una referencia polimórfica).
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

-- -------------------------------------------------------------
-- FKs diferidas hacia "documento"
-- Se agregan hasta aquí porque cotizacion y pago se crean ANTES
-- que documento, y documento no puede referenciarlas primero sin
-- crear una dependencia circular en la creación de tablas.
-- -------------------------------------------------------------
ALTER TABLE cotizacion ADD CONSTRAINT fk_cotizacion_documento
    FOREIGN KEY (documento_original_id) REFERENCES documento(id);
ALTER TABLE pago ADD CONSTRAINT fk_pago_documento
    FOREIGN KEY (comprobante_documento_id) REFERENCES documento(id);

SET FOREIGN_KEY_CHECKS = 1;