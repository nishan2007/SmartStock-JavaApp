-- Stable machine identities for parent rows that can originate on separate devices.
-- Existing child/history rows were already covered by workflow_sync_identity_setup.sql.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE customer_accounts
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE custom_orders
ADD COLUMN IF NOT EXISTS sync_uuid UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX IF NOT EXISTS sales_sync_uuid_key
ON sales(sync_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS customer_accounts_sync_uuid_key
ON customer_accounts(sync_uuid);

CREATE UNIQUE INDEX IF NOT EXISTS custom_orders_sync_uuid_key
ON custom_orders(sync_uuid);
