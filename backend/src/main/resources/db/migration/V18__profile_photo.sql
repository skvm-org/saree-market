-- Profile pictures: the storage key of an account's picture and when it last changed (clients use the time as a
-- cache-busting version). Both are NULL for an account with no picture. Additive, so the previous release keeps running.
ALTER TABLE app_user
    ADD COLUMN photo_key        VARCHAR(300),
    ADD COLUMN photo_updated_at TIMESTAMPTZ;
