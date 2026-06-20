ALTER TABLE company_customization
ADD COLUMN IF NOT EXISTS price_tag_templates TEXT NOT NULL DEFAULT '';
