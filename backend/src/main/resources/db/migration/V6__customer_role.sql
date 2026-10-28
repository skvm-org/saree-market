-- Resellers become customers: everyone who buys is a CUSTOMER.
DO $$
DECLARE c text;
BEGIN
    SELECT conname INTO c FROM pg_constraint
    WHERE conrelid = 'app_user'::regclass AND contype = 'c' AND pg_get_constraintdef(oid) LIKE '%role%';
    IF c IS NOT NULL THEN
        EXECUTE format('ALTER TABLE app_user DROP CONSTRAINT %I', c);
    END IF;
END $$;

UPDATE app_user SET role = 'CUSTOMER' WHERE role = 'RESELLER';
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('SELLER', 'CUSTOMER', 'ADMIN'));
