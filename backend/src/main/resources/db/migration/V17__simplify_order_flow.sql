-- Simpler order flow: an order is CONFIRMED the moment the customer submits the UTR, the seller adds courier and
-- tracking to ship it, and the customer marks it received. PAYMENT_SUBMITTED, PAYMENT_REJECTED and PACKED are no
-- longer produced. This migration only moves existing rows; it does not narrow the status CHECK constraint or drop
-- idx_order_rejected_deadline, so the previous release can still run against the migrated data. A later migration
-- removes them once this flow has settled.

-- Orders whose payment the seller rejected will not get a correction window any more: cancel them and put their
-- stock back on sale, the same capped add-back as StockReturn (10000 per saree).
UPDATE item i
SET quantity = LEAST(i.quantity + l.q, 10000)
FROM (SELECT ol.item_id, sum(ol.quantity) AS q
      FROM order_line ol
      JOIN customer_order o ON o.id = ol.order_id
      WHERE o.status = 'PAYMENT_REJECTED' AND ol.item_id IS NOT NULL
      GROUP BY ol.item_id) l
WHERE i.id = l.item_id;

INSERT INTO order_status_history (order_id, status)
SELECT id, 'CANCELLED' FROM customer_order WHERE status = 'PAYMENT_REJECTED';

UPDATE customer_order
SET status = 'CANCELLED', cancelled_by = 'SYSTEM',
    cancel_reason = 'The order flow changed; the payment could not be matched',
    payment_deadline = NULL, customer_unread = TRUE, updated_at = now()
WHERE status = 'PAYMENT_REJECTED';

-- Orders waiting for the seller's payment check are now simply confirmed, and the seller is told they are new.
INSERT INTO order_status_history (order_id, status)
SELECT id, 'CONFIRMED' FROM customer_order WHERE status = 'PAYMENT_SUBMITTED';

UPDATE customer_order
SET status = 'CONFIRMED', seller_unread = TRUE, updated_at = now()
WHERE status = 'PAYMENT_SUBMITTED';

-- Packed orders go back one step to "to ship". They already have a CONFIRMED history row.
UPDATE customer_order
SET status = 'CONFIRMED', updated_at = now()
WHERE status = 'PACKED';
