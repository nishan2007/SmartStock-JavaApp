CREATE TABLE IF NOT EXISTS storefront.project_media (
 media_id uuid PRIMARY KEY,
 project_id uuid NOT NULL REFERENCES storefront.projects(project_id) ON DELETE CASCADE,
 location_id integer NOT NULL,
 role text NOT NULL CHECK(role IN ('FINAL','DETAIL','PROCESS','BEFORE','AFTER')),
 caption text NOT NULL DEFAULT '' CHECK(char_length(caption)<=160),
 asset_reference text NOT NULL,
 position integer NOT NULL CHECK(position BETWEEN 0 AND 5),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(project_id,position)
);
CREATE INDEX IF NOT EXISTS storefront_project_media_project ON storefront.project_media(project_id,position);
INSERT INTO storefront.schema_version(version) VALUES(10) ON CONFLICT DO NOTHING;
