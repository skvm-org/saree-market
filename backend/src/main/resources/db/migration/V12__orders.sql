-- Payment holds reserve stock for 2 hours while a customer pays; submitting proof turns a hold into an order.
CREATE TABLE payment_hold (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id   BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    seller_id     BIGINT       NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    items_total   INTEGER      NOT NULL,
    shipping_fee  INTEGER      NOT NULL,
    grand_total   INTEGER      NOT NULL,
    address       JSONB        NOT NULL,
    customer_note VARCHAR(300),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at    TIMESTAMPTZ  NOT NULL,
    UNIQUE (customer_id, seller_id)
);

CREATE INDEX idx_hold_expires ON payment_hold (expires_at);

CREATE TABLE hold_line (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    hold_id    BIGINT       NOT NULL REFERENCES payment_hold (id) ON DELETE CASCADE,
    item_id    BIGINT       REFERENCES item (id) ON DELETE SET NULL,
    title      VARCHAR(200) NOT NULL,
    colour     VARCHAR(50),
    unit_price INTEGER      NOT NULL,
    quantity   INTEGER      NOT NULL CHECK (quantity > 0)
);

CREATE TABLE customer_order (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    seller_id         BIGINT       NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    status            VARCHAR(20)  NOT NULL
                      CHECK (status IN ('PAYMENT_SUBMITTED', 'PAYMENT_REJECTED', 'CONFIRMED', 'PACKED', 'SHIPPED',
                                        'DELIVERED', 'CANCELLED')),
    items_total       INTEGER      NOT NULL,
    shipping_fee      INTEGER      NOT NULL,
    grand_total       INTEGER      NOT NULL,
    address           JSONB        NOT NULL,
    customer_note     VARCHAR(300),
    payment_reference VARCHAR(12),
    payment_proof_key VARCHAR(300),
    rejection_reason  VARCHAR(200),
    rejection_count   INTEGER      NOT NULL DEFAULT 0,
    payment_deadline  TIMESTAMPTZ,
    cancel_reason     VARCHAR(200),
    cancelled_by      VARCHAR(10),
    courier           VARCHAR(60),
    tracking_number   VARCHAR(60),
    customer_unread   BOOLEAN      NOT NULL DEFAULT FALSE,
    seller_unread     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One real payment (UTR) cannot back two live orders; a cancelled order frees its UTR.
CREATE UNIQUE INDEX uq_order_utr ON customer_order (payment_reference)
    WHERE payment_reference IS NOT NULL AND status <> 'CANCELLED';
CREATE INDEX idx_order_seller ON customer_order (seller_id, status, created_at);
CREATE INDEX idx_order_customer ON customer_order (customer_id, created_at);
CREATE INDEX idx_order_rejected_deadline ON customer_order (payment_deadline) WHERE status = 'PAYMENT_REJECTED';

CREATE TABLE order_line (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   BIGINT       NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    item_id    BIGINT       REFERENCES item (id) ON DELETE SET NULL,
    title      VARCHAR(200) NOT NULL,
    colour     VARCHAR(50),
    unit_price INTEGER      NOT NULL,
    quantity   INTEGER      NOT NULL CHECK (quantity > 0)
);

CREATE TABLE order_status_history (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id BIGINT      NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    status   VARCHAR(20) NOT NULL,
    at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_order_history ON order_status_history (order_id, id);
