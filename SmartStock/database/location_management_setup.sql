-- Location management setup for SmartStock.
-- Adds the permission for the Admin > Locations screen and makes sure store timezone exists.

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS timezone TEXT NOT NULL DEFAULT 'America/New_York';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS receipt_store_code TEXT NOT NULL DEFAULT '0001';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line1 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line2 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_address_line3 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_phone_line1 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_phone_line2 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_email_line1 TEXT NOT NULL DEFAULT '';
ALTER TABLE locations
ADD COLUMN IF NOT EXISTS company_email_line2 TEXT NOT NULL DEFAULT '';

UPDATE locations
SET receipt_store_code = LPAD(location_id::text, 4, '0')
WHERE receipt_store_code IS NULL
   OR receipt_store_code = '';

UPDATE locations
SET timezone = 'America/New_York'
WHERE timezone IS NULL OR timezone = '';

DO $$
BEGIN
    IF to_regclass('public.company_customization') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'company_customization'
             AND column_name = 'company_address_line1'
       ) THEN
        EXECUTE $sql$
            UPDATE locations l
            SET company_address_line1 = COALESCE(NULLIF(l.company_address_line1, ''), cc.company_address_line1, ''),
                company_address_line2 = COALESCE(NULLIF(l.company_address_line2, ''), cc.company_address_line2, ''),
                company_address_line3 = COALESCE(NULLIF(l.company_address_line3, ''), cc.company_address_line3, ''),
                company_phone_line1 = COALESCE(NULLIF(l.company_phone_line1, ''), cc.company_phone_line1, ''),
                company_phone_line2 = COALESCE(NULLIF(l.company_phone_line2, ''), cc.company_phone_line2, ''),
                company_email_line1 = COALESCE(NULLIF(l.company_email_line1, ''), cc.company_email_line1, ''),
                company_email_line2 = COALESCE(NULLIF(l.company_email_line2, ''), cc.company_email_line2, '')
            FROM company_customization cc
            WHERE cc.location_id = l.location_id
              AND (l.company_address_line1 = '' OR l.company_address_line2 = '' OR l.company_address_line3 = ''
                   OR l.company_phone_line1 = '' OR l.company_phone_line2 = ''
                   OR l.company_email_line1 = '' OR l.company_email_line2 = '')
        $sql$;
    END IF;
END $$;

ALTER TABLE IF EXISTS company_customization
DROP COLUMN IF EXISTS company_address_line1,
DROP COLUMN IF EXISTS company_address_line2,
DROP COLUMN IF EXISTS company_address_line3,
DROP COLUMN IF EXISTS company_phone_line1,
DROP COLUMN IF EXISTS company_phone_line2,
DROP COLUMN IF EXISTS company_email_line1,
DROP COLUMN IF EXISTS company_email_line2;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'LOCATION_MANAGEMENT', 'Location Management'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'LOCATION_MANAGEMENT'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'LOCATION_MANAGEMENT'
WHERE UPPER(r.role_name) IN ('ADMIN', 'MANAGER')
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );
