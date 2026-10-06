-- Add the staff-managed public featured campaign to existing v2 store databases.
ALTER TABLE storefront.settings
 ADD COLUMN campaign_topic text NOT NULL DEFAULT '3D Printing' CHECK (char_length(campaign_topic) BETWEEN 1 AND 100),
 ADD COLUMN campaign_eyebrow text NOT NULL DEFAULT 'Now at Deckers · 3D Printing' CHECK (char_length(campaign_eyebrow) BETWEEN 1 AND 120),
 ADD COLUMN campaign_headline text NOT NULL DEFAULT 'Your Ideas. Made Real.' CHECK (char_length(campaign_headline) BETWEEN 1 AND 150),
 ADD COLUMN campaign_description text NOT NULL DEFAULT 'Custom 3D printing is now available at Deckers.' CHECK (char_length(campaign_description) BETWEEN 1 AND 300),
 ADD COLUMN campaign_steps text NOT NULL DEFAULT 'Upload your design · Choose your material · We print it' CHECK (char_length(campaign_steps) BETWEEN 1 AND 200),
 ADD COLUMN campaign_primary text NOT NULL DEFAULT 'Start a 3D Print' CHECK (char_length(campaign_primary) BETWEEN 1 AND 60),
 ADD COLUMN campaign_secondary text NOT NULL DEFAULT 'Explore 3D Printing' CHECK (char_length(campaign_secondary) BETWEEN 1 AND 60),
 ADD COLUMN campaign_visual text NOT NULL DEFAULT '3D_PRINT' CHECK (campaign_visual IN ('3D_PRINT','EDITORIAL'));
INSERT INTO storefront.schema_version(version) VALUES(3) ON CONFLICT DO NOTHING;
