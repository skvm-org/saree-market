-- Sarees a customer has saved for later.
CREATE TABLE wishlist_entry (
    customer_id BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    item_id     BIGINT      NOT NULL REFERENCES item (id) ON DELETE CASCADE,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, item_id)
);

CREATE INDEX idx_wishlist_customer ON wishlist_entry (customer_id, added_at DESC);
