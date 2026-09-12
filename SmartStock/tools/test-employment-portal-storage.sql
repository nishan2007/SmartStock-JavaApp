\set ON_ERROR_STOP on
-- Run only after restoring the disposable portal database into portal_storage_test.
DO $$ BEGIN IF current_database()<>'portal_storage_test' THEN RAISE EXCEPTION 'Disposable portal_storage_test database required'; END IF; END $$;
CREATE ROLE anon;
CREATE ROLE authenticated;
CREATE SCHEMA storage;
CREATE TABLE storage.buckets(id text PRIMARY KEY,name text,public boolean,file_size_limit bigint,allowed_mime_types text[]);
CREATE TABLE storage.objects(id integer PRIMARY KEY,bucket_id text);
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;
CREATE FUNCTION public.current_app_user_id() RETURNS integer LANGUAGE sql STABLE AS $$ SELECT NULL::integer $$;
\ir ../database/migrations/v1_after/20260909120000_employment_portal.sql
GRANT USAGE ON SCHEMA storage TO anon,authenticated;
GRANT ALL ON storage.objects TO anon,authenticated;
-- Simulate an overly broad pre-existing grant: the portal's restrictive policy must still win.
CREATE POLICY broad_legacy_policy ON storage.objects FOR ALL TO anon,authenticated USING(TRUE) WITH CHECK(TRUE);
INSERT INTO storage.objects VALUES(1,'employment-applications'),(2,'Product Images');
SET ROLE authenticated;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='employment-applications') THEN RAISE EXCEPTION 'Applicant read private files'; END IF;
 BEGIN INSERT INTO storage.objects VALUES(3,'employment-applications'); RAISE EXCEPTION 'Applicant uploaded private file'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 BEGIN INSERT INTO storage.objects VALUES(4,'Product Images'); RAISE EXCEPTION 'Applicant uploaded product image'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
 UPDATE storage.objects SET id=5 WHERE id=2;
 IF FOUND THEN RAISE EXCEPTION 'Applicant modified product image'; END IF;
END $$;
RESET ROLE;
SET ROLE anon;
DO $$ BEGIN IF EXISTS(SELECT 1 FROM storage.objects WHERE bucket_id='employment-applications') THEN RAISE EXCEPTION 'Anonymous read private files'; END IF; END $$;
RESET ROLE;
-- Existing active employees retain the ability to use their permitted product bucket.
CREATE OR REPLACE FUNCTION public.current_app_user_id() RETURNS integer LANGUAGE sql STABLE AS $$ SELECT 1 $$;
SET ROLE authenticated;
INSERT INTO storage.objects VALUES(6,'Product Images');
DO $$ BEGIN
 BEGIN INSERT INTO storage.objects VALUES(7,'employment-applications'); RAISE EXCEPTION 'Employee bypassed manager application API'; EXCEPTION WHEN insufficient_privilege THEN NULL; END;
END $$;
RESET ROLE;
SELECT 'Portal Storage isolation checks passed' AS result;
