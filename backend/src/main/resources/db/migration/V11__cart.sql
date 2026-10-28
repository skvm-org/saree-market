-- A customer's cart: sarees they mean to order. It reserves nothing; stock is taken at checkout.
CREATE TABLE cart_line (
    customer_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    item_id     BIGINT      NOT NULL REFERENCES item (id) ON DELETE CASCADE,
    quantity    INTEGER     NOT NULL CHECK (quantity > 0),
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, item_id)
);
