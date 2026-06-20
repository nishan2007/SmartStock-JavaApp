-- Add the sale device display label that normal_sales_audit_setup.sql expects.
-- Safe to run more than once.

ALTER TABLE sales
ADD COLUMN IF NOT EXISTS device_name TEXT;

CREATE INDEX IF NOT EXISTS sales_device_name_idx
ON sales(device_name, created_at DESC);

UPDATE sales s
SET device_name = COALESCE(NULLIF(TRIM(d.device_name), ''), NULLIF(TRIM(d.hostname), ''), s.device_id)
FROM devices d
WHERE s.device_name IS NULL
  AND s.device_id IS NOT NULL
  AND d.device_id::text = s.device_id::text;

UPDATE sales
SET device_name = device_id
WHERE device_name IS NULL
  AND device_id IS NOT NULL;
