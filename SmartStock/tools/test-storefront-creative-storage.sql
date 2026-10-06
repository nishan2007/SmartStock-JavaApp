\set ON_ERROR_STOP on
-- Run only in a disposable PostgreSQL database named storefront_creative_test.
DO $$ BEGIN IF current_database()<>'storefront_creative_test' THEN RAISE EXCEPTION 'Disposable storefront_creative_test database required'; END IF; END $$;
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF;
 IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; END $$;
CREATE SCHEMA storage;
CREATE TABLE storage.buckets(id text PRIMARY KEY,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
CREATE TABLE storage.objects(id integer PRIMARY KEY,bucket_id text);
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;
GRANT USAGE ON SCHEMA storage TO anon,authenticated;
GRANT ALL ON storage.objects TO anon,authenticated;
CREATE POLICY broad_legacy_policy ON storage.objects FOR ALL TO anon,authenticated USING(TRUE) WITH CHECK(TRUE);
\ir ../database/migrations/v1_after/20260927120000_storefront_private_creative_storage.sql
INSERT INTO storage.objects VALUES(1,'deckers-creative'),(2,'Product Images');
DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM storage.buckets WHERE id='deckers-creative' AND public=FALSE) THEN RAISE EXCEPTION 'Creative bucket is not private'; END IF; END $$;
SET ROLE anon;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='deckers-creative') THEN RAISE EXCEPTION 'Anonymous user read private creative file'; END IF;
 BEGIN INSERT INTO storage.objects VALUES(3,'deckers-creative');RAISE EXCEPTION 'Anonymous user uploaded private creative file';EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SET ROLE authenticated;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='deckers-creative') THEN RAISE EXCEPTION 'Authenticated user read private creative file'; END IF;
 BEGIN INSERT INTO storage.objects VALUES(4,'deckers-creative');RAISE EXCEPTION 'Authenticated user uploaded private creative file';EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 IF NOT EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='Product Images') THEN RAISE EXCEPTION 'Unrelated product bucket became hidden'; END IF;
END $$;
RESET ROLE;
SELECT 'Creative storage isolation checks passed' AS result;
