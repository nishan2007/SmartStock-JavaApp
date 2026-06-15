-- Store timezone setup for SmartStock.
-- Reports use the selected store timezone for day boundaries.

ALTER TABLE locations
ADD COLUMN IF NOT EXISTS timezone TEXT NOT NULL DEFAULT 'America/New_York';

UPDATE locations
SET timezone = 'America/New_York'
WHERE timezone IS NULL OR timezone = '';
