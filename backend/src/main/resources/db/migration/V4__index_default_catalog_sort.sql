-- The default catalog view is "available items, newest first"; this lets PostgreSQL read it in order.
CREATE INDEX idx_item_available_newest ON item (created_at DESC) WHERE status = 'AVAILABLE';
