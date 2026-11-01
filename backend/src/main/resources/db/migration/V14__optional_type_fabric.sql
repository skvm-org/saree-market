-- Saree type and fabric are being retired. First step: a collection no longer has to have them.
-- The columns, the reference tables and their endpoints stay until every installed app has stopped using them.
ALTER TABLE collection ALTER COLUMN saree_type_id DROP NOT NULL;
ALTER TABLE collection ALTER COLUMN fabric_id DROP NOT NULL;
