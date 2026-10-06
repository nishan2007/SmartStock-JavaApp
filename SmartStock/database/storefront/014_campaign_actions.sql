ALTER TABLE storefront.settings
 ADD COLUMN IF NOT EXISTS campaign_primary_action text NOT NULL DEFAULT 'START' CHECK (campaign_primary_action IN ('START','EXPLORE','SHOP','MADE')),
 ADD COLUMN IF NOT EXISTS campaign_secondary_action text NOT NULL DEFAULT 'EXPLORE' CHECK (campaign_secondary_action IN ('START','EXPLORE','SHOP','MADE'));
UPDATE storefront.settings SET campaign_primary_action='EXPLORE',campaign_secondary_action='SHOP'
 WHERE campaign_topic<>'3D Printing';
INSERT INTO storefront.schema_version(version) VALUES(14) ON CONFLICT DO NOTHING;
