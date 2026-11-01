-- Saree type and fabric are gone from the app, the API and the admin endpoints. This cannot be undone:
-- take an RDS snapshot before it runs.
DROP INDEX IF EXISTS idx_collection_type;
DROP INDEX IF EXISTS idx_collection_fabric;
ALTER TABLE collection DROP COLUMN saree_type_id;
ALTER TABLE collection DROP COLUMN fabric_id;
DROP TABLE saree_type;
DROP TABLE fabric;
