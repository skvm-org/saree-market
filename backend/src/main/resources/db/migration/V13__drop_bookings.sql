-- Bookings are replaced by carts and orders. Sarees a booking was holding go back on sale (a held saree was a real
-- piece, so it has at least one), then the requests and the ON_HOLD status are removed.
UPDATE item SET status = 'AVAILABLE', quantity = GREATEST(quantity, 1) WHERE status = 'ON_HOLD';

DROP TABLE booking_request;

DO $$
DECLARE c text;
BEGIN
    SELECT conname INTO c FROM pg_constraint
    WHERE conrelid = 'item'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%status%';
    IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE item DROP CONSTRAINT %I', c);
    END IF;
END $$;

ALTER TABLE item ADD CONSTRAINT item_status_check CHECK (status IN ('AVAILABLE', 'SOLD_OUT'));
