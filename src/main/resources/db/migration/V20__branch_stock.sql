-- V20: Customer home salon + branch stock

ALTER TABLE customer ADD COLUMN IF NOT EXISTS home_salon_id BIGINT REFERENCES salon(id);

UPDATE customer SET home_salon_id = salon_id WHERE home_salon_id IS NULL;

CREATE TABLE branch_stock (
    id         BIGSERIAL PRIMARY KEY,
    salon_id   BIGINT  NOT NULL REFERENCES salon(id) ON DELETE CASCADE,
    product_id BIGINT  NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    quantity   INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    updated_at TIMESTAMP,
    CONSTRAINT uk_branch_stock UNIQUE (salon_id, product_id)
);

-- Initialize branch_stock from product.stock_quantity for default salon
INSERT INTO branch_stock (salon_id, product_id, quantity, updated_at)
SELECT salon_id, id, stock_quantity, NOW() FROM product
ON CONFLICT DO NOTHING;
