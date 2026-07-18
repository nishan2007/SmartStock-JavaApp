ALTER TABLE locations
ADD COLUMN IF NOT EXISTS balance_sheet_recipient_email TEXT NOT NULL DEFAULT '';
