-- Sellers say how many pieces of a saree they have; orders will count it down.
ALTER TABLE item ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1 CHECK (quantity >= 0 AND quantity <= 10000);
UPDATE item SET quantity = 0 WHERE status IN ('SOLD_OUT', 'ON_HOLD');
