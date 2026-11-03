-- Order updates (confirmed, packed, shipped, delivered, rejected, cancelled) join the customer's notification list.
-- Such a row points at an order instead of a collection.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check
    CHECK (type IN ('NEW_COLLECTION', 'ITEMS_ADDED', 'BACK_IN_STOCK', 'ORDER_UPDATE'));

ALTER TABLE notification ALTER COLUMN collection_id DROP NOT NULL;
ALTER TABLE notification
    ADD COLUMN order_id     BIGINT REFERENCES customer_order (id) ON DELETE CASCADE,
    ADD COLUMN order_status VARCHAR(20);

ALTER TABLE notification ADD CONSTRAINT notification_target_check CHECK (
    (type = 'ORDER_UPDATE' AND order_id IS NOT NULL AND order_status IS NOT NULL AND collection_id IS NULL)
    OR (type <> 'ORDER_UPDATE' AND collection_id IS NOT NULL));
