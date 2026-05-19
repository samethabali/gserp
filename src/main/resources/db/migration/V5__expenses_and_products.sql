-- V5: Masraflar ve ürün/stok yönetimi

CREATE TABLE expense (
    id           BIGSERIAL PRIMARY KEY,
    description  VARCHAR(255) NOT NULL,
    amount       NUMERIC(12,2) NOT NULL,
    category     VARCHAR(32) NOT NULL,
    expense_date DATE NOT NULL,
    staff_id     BIGINT,
    notes        TEXT,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_expense_date ON expense(expense_date);

CREATE TABLE product (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    category            VARCHAR(64),
    price               NUMERIC(12,2) NOT NULL,
    stock_quantity      INTEGER NOT NULL DEFAULT 0,
    low_stock_threshold INTEGER NOT NULL DEFAULT 5,
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP
);

CREATE TABLE product_sale (
    id             BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL REFERENCES product(id),
    quantity       INTEGER NOT NULL,
    unit_price     NUMERIC(12,2) NOT NULL,
    total_price    NUMERIC(12,2) NOT NULL,
    appointment_id BIGINT REFERENCES appointment(id) ON DELETE SET NULL,
    customer_phone VARCHAR(64),
    customer_name  VARCHAR(255),
    sold_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    staff_id       BIGINT
);
CREATE INDEX idx_product_sale_sold_at  ON product_sale(sold_at);
CREATE INDEX idx_product_sale_product  ON product_sale(product_id);
