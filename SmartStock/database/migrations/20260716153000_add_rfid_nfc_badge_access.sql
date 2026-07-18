ALTER TABLE users
    ADD COLUMN IF NOT EXISTS badge_rotated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS badge_rotated_by_user_id INTEGER,
    ADD COLUMN IF NOT EXISTS badge_rotated_by_name TEXT;

ALTER TABLE company_customization
    ADD COLUMN IF NOT EXISTS badge_template_nfc_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS badge_template_nfc_payload TEXT NOT NULL DEFAULT '{badge_id}',
    ADD COLUMN IF NOT EXISTS badge_template_nfc_writer_command TEXT NOT NULL DEFAULT '',
    ADD COLUMN IF NOT EXISTS badge_template_nfc_verify_command TEXT NOT NULL DEFAULT '';

ALTER TABLE company_customization
    ALTER COLUMN badge_template_back_instructions
    SET DEFAULT 'Scan, swipe, or tap this badge for SmartStock access.';

UPDATE company_customization
SET badge_template_back_instructions = 'Scan, swipe, or tap this badge for SmartStock access.',
    updated_at = NOW()
WHERE badge_template_back_instructions = 'Scan or swipe this badge for SmartStock access.';
