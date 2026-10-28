-- Booking requests: a reseller asks a seller to hold one saree. An accepted request puts the item ON_HOLD.
ALTER TABLE item DROP CONSTRAINT item_status_check;
ALTER TABLE item ADD CONSTRAINT item_status_check CHECK (status IN ('AVAILABLE', 'ON_HOLD', 'SOLD_OUT'));

CREATE TABLE booking_request (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    item_id         BIGINT      NOT NULL REFERENCES item (id) ON DELETE CASCADE,
    reseller_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    seller_id       BIGINT      NOT NULL REFERENCES seller_profile (user_id) ON DELETE CASCADE,
    status          VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'SOLD', 'CLOSED')),
    note            VARCHAR(300),
    hold_expires_at TIMESTAMPTZ,
    reseller_unread BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One open request per reseller per saree, and at most one accepted hold per saree.
CREATE UNIQUE INDEX uq_booking_open_per_reseller ON booking_request (item_id, reseller_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
CREATE UNIQUE INDEX uq_booking_one_hold_per_item ON booking_request (item_id) WHERE status = 'ACCEPTED';
CREATE INDEX idx_booking_seller ON booking_request (seller_id, status, created_at);
CREATE INDEX idx_booking_reseller ON booking_request (reseller_id, created_at);
