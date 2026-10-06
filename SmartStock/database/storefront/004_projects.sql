-- Curated portfolio entries. Source orders stay private in the local store.
CREATE TABLE storefront.projects (
 project_id uuid PRIMARY KEY,
 location_id integer NOT NULL,
 source_custom_order_id bigint,
 status text NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED','FEATURED','ARCHIVED')),
 title text NOT NULL DEFAULT '' CHECK (char_length(title)<=150),
 summary text NOT NULL DEFAULT '' CHECK (char_length(summary)<=600),
 category text NOT NULL DEFAULT '' CHECK (char_length(category)<=80),
 materials text NOT NULL DEFAULT '' CHECK (char_length(materials)<=300),
 production_method text NOT NULL DEFAULT '' CHECK (char_length(production_method)<=300),
 customization text NOT NULL DEFAULT '' CHECK (char_length(customization)<=300),
 tags jsonb NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags)='array'),
 starting_price numeric(12,2) CHECK (starting_price>=0),
 cover_reference text NOT NULL DEFAULT '',
 created_at timestamptz NOT NULL DEFAULT now(),
 updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(location_id,source_custom_order_id)
);
CREATE INDEX storefront_projects_public ON storefront.projects(location_id,status,updated_at DESC);
INSERT INTO storefront.schema_version(version) VALUES(4) ON CONFLICT DO NOTHING;
