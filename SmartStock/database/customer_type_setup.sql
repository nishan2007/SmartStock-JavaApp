-- Customer type/category setup for SmartStock.
-- Add future categories by inserting rows into customer_types.

CREATE TABLE IF NOT EXISTS customer_types (
    customer_type_id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO customer_types (name, description)
VALUES ('General', 'Default customer category')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE customer_accounts
ADD COLUMN IF NOT EXISTS customer_type_id INTEGER REFERENCES customer_types(customer_type_id);

CREATE SEQUENCE IF NOT EXISTS customer_account_ca_number_seq;
CREATE SEQUENCE IF NOT EXISTS customer_account_ba_number_seq;

SELECT setval(
    'customer_account_ca_number_seq',
    GREATEST((SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) FROM customer_accounts WHERE account_number ~ '^CA-[0-9]+$'), 1),
    (SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) > 0 FROM customer_accounts WHERE account_number ~ '^CA-[0-9]+$')
);

SELECT setval(
    'customer_account_ba_number_seq',
    GREATEST((SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) FROM customer_accounts WHERE account_number ~ '^BA-[0-9]+$'), 1),
    (SELECT COALESCE(MAX(CAST(SUBSTRING(account_number FROM 4) AS INTEGER)), 0) > 0 FROM customer_accounts WHERE account_number ~ '^BA-[0-9]+$')
);

CREATE OR REPLACE FUNCTION assign_customer_account_number()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NULLIF(TRIM(NEW.account_number), '') IS NULL THEN
        IF COALESCE(NEW.is_business, FALSE) THEN
            NEW.account_number := 'BA-' || LPAD(NEXTVAL('customer_account_ba_number_seq')::TEXT, 6, '0');
        ELSE
            NEW.account_number := 'CA-' || LPAD(NEXTVAL('customer_account_ca_number_seq')::TEXT, 6, '0');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS assign_customer_account_number_before_insert ON customer_accounts;
CREATE TRIGGER assign_customer_account_number_before_insert
BEFORE INSERT ON customer_accounts
FOR EACH ROW
EXECUTE FUNCTION assign_customer_account_number();

UPDATE customer_accounts ca
SET customer_type_id = ct.customer_type_id
FROM customer_types ct
WHERE ct.name = 'General'
  AND ca.customer_type_id IS NULL;

CREATE INDEX IF NOT EXISTS customer_accounts_customer_type_id_idx
ON customer_accounts(customer_type_id);
