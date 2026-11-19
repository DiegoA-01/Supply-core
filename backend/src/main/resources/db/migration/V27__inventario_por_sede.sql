-- Inventario por sede: cada sede maneja su propio mínimo y máximo por producto, como un punto de venta independiente.
-- Es opcional: un producto sin fila para una sede se guía en esa sede solo por su consumo. El mínimo y el máximo del
-- producto (tabla producto) siguen siendo los de toda la empresa y sirven para el resumen global.

CREATE TABLE producto_sede (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id   BIGINT        NOT NULL,
    producto_id  BIGINT        NOT NULL,
    sede_id      BIGINT        NOT NULL,
    stock_minimo DECIMAL(18,4) NOT NULL DEFAULT 0,
    stock_maximo DECIMAL(18,4) NULL,
    UNIQUE KEY uq_producto_sede (empresa_id, producto_id, sede_id),
    CONSTRAINT chk_producto_sede_minimo CHECK (stock_minimo >= 0),
    CONSTRAINT chk_producto_sede_maximo CHECK (stock_maximo IS NULL OR stock_maximo >= stock_minimo),
    CONSTRAINT fk_producto_sede_empresa FOREIGN KEY (empresa_id) REFERENCES empresa(id),
    CONSTRAINT fk_producto_sede_producto FOREIGN KEY (producto_id) REFERENCES producto(id),
    CONSTRAINT fk_producto_sede_sede FOREIGN KEY (sede_id) REFERENCES sede(id)
) ENGINE=InnoDB;
