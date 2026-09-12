-- Applicant identities can sign in before employment; local users remain permission gated.
ALTER TABLE employee_registrations DROP CONSTRAINT IF EXISTS employee_registrations_status_check;
ALTER TABLE employee_registrations DROP CONSTRAINT IF EXISTS employee_registrations_check;
ALTER TABLE employee_registrations ADD CONSTRAINT employee_registrations_status_check CHECK
 (status IN ('SUBMITTING','DRAFT','PENDING','UNDER_REVIEW','INTERVIEW','INFORMATION_REQUESTED','APPROVING','APPROVED','REJECTED','WITHDRAWN'));
ALTER TABLE employee_registrations ALTER COLUMN date_of_birth DROP NOT NULL;
ALTER TABLE employee_registrations ADD COLUMN IF NOT EXISTS application_json JSONB NOT NULL DEFAULT '{}';
ALTER TABLE employee_registrations ADD COLUMN IF NOT EXISTS revision INTEGER NOT NULL DEFAULT 0;
ALTER TABLE employee_registrations ADD COLUMN IF NOT EXISTS portal_identity BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS employee_application_events (
 event_id BIGSERIAL PRIMARY KEY,
 registration_id UUID NOT NULL REFERENCES employee_registrations(registration_id),
 status TEXT NOT NULL,
 message TEXT NOT NULL DEFAULT '',
 internal_note TEXT NOT NULL DEFAULT '',
 actor_id INTEGER REFERENCES users(user_id),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS employee_application_events_owner_idx ON employee_application_events(registration_id,event_id);
CREATE TABLE IF NOT EXISTS employee_application_revisions (
 registration_id UUID NOT NULL REFERENCES employee_registrations(registration_id),
 revision INTEGER NOT NULL,
 application_json JSONB NOT NULL,
 attachments_json JSONB NOT NULL DEFAULT '[]',
 submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(registration_id,revision)
);
CREATE TABLE IF NOT EXISTS employee_application_attachments (
 attachment_id UUID PRIMARY KEY,
 registration_id UUID NOT NULL REFERENCES employee_registrations(registration_id),
 filename TEXT NOT NULL,
 category TEXT NOT NULL CHECK(category IN ('exam_results','identification','application','employment_letter','recommendation','other')),
 description TEXT NOT NULL DEFAULT '',
 content_type TEXT NOT NULL,
 byte_size BIGINT NOT NULL CHECK(byte_size>0),
 page_count INTEGER NOT NULL DEFAULT 1,
 object_path TEXT NOT NULL UNIQUE,
 removed BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS employee_application_attachments_owner_idx ON employee_application_attachments(registration_id,removed);
INSERT INTO employee_application_events(registration_id,status,message)
 SELECT r.registration_id,r.status,'Existing application imported.' FROM employee_registrations r
 WHERE NOT EXISTS(SELECT 1 FROM employee_application_events e WHERE e.registration_id=r.registration_id);
ALTER TABLE employee_application_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_application_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_application_attachments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON employee_application_events,employee_application_revisions,employee_application_attachments FROM PUBLIC;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON employee_application_events,employee_application_revisions,employee_application_attachments FROM anon;
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON employee_application_events,employee_application_revisions,employee_application_attachments FROM authenticated;
 END IF;
END $$;

-- Cloud-only storage provisioning; local installations have no storage schema.
DO $portal_storage$ BEGIN
 IF to_regclass('storage.buckets') IS NOT NULL THEN
  INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
   VALUES('employment-applications','employment-applications',FALSE,10485760,ARRAY['image/jpeg','image/png','application/pdf'])
   ON CONFLICT(id) DO UPDATE SET public=FALSE,file_size_limit=10485760,allowed_mime_types=EXCLUDED.allowed_mime_types;
  DROP POLICY IF EXISTS employment_portal_server_only ON storage.objects;
  CREATE POLICY employment_portal_server_only ON storage.objects AS RESTRICTIVE FOR ALL TO anon,authenticated
   USING(bucket_id <> 'employment-applications') WITH CHECK(bucket_id <> 'employment-applications');
  DROP POLICY IF EXISTS employment_portal_employee_upload_guard ON storage.objects;
  CREATE POLICY employment_portal_employee_upload_guard ON storage.objects AS RESTRICTIVE FOR INSERT TO authenticated
   WITH CHECK(public.current_app_user_id() IS NOT NULL);
  DROP POLICY IF EXISTS employment_portal_employee_update_guard ON storage.objects;
  CREATE POLICY employment_portal_employee_update_guard ON storage.objects AS RESTRICTIVE FOR UPDATE TO authenticated
   USING(public.current_app_user_id() IS NOT NULL) WITH CHECK(public.current_app_user_id() IS NOT NULL);
 END IF;
END $portal_storage$;
