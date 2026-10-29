-- A customer's saved delivery addresses; at most one is the default.
CREATE TABLE address (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name        VARCHAR(100) NOT NULL,
    phone       VARCHAR(10)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200),
    city        VARCHAR(100) NOT NULL,
    state       VARCHAR(100) NOT NULL,
    pin         VARCHAR(6)   NOT NULL,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_address_customer ON address (customer_id);
CREATE UNIQUE INDEX uq_address_default ON address (customer_id) WHERE is_default;
