CREATE TABLE productos (
                           id      BIGSERIAL PRIMARY KEY,
                           sku     VARCHAR(30)    NOT NULL UNIQUE,
                           nombre  VARCHAR(100)   NOT NULL,
                           precio  NUMERIC(10,2)  NOT NULL CHECK (precio > 0),
                           stock   INTEGER        NOT NULL CHECK (stock >= 0)
);

CREATE TABLE pedidos (
                         id          BIGSERIAL PRIMARY KEY,
                         producto_id BIGINT        NOT NULL REFERENCES productos(id),
                         cantidad    INTEGER       NOT NULL CHECK (cantidad > 0),
                         total       NUMERIC(10,2) NOT NULL,
                         estado      VARCHAR(20)   NOT NULL,
                         creado_en   TIMESTAMP     NOT NULL
);