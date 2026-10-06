INSERT INTO storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('employee files', 'employee files', false, NULL, NULL),
    ('deckers-creative', 'deckers-creative', false, 8388608,
        ARRAY['image/jpeg','image/png','application/pdf','application/octet-stream']),
    ('Product Images', 'Product Images', true, 52428800,
        ARRAY['image/jpeg','image/png','image/gif','image/bmp','image/webp']),
    ('smartstock-releases', 'smartstock-releases', false, 50000000,
        ARRAY['application/zip'])
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit,
    allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "Anyone can view product images" ON storage.objects;
DROP POLICY IF EXISTS deckers_creative_server_only ON storage.objects;
CREATE POLICY deckers_creative_server_only ON storage.objects AS RESTRICTIVE FOR ALL TO anon,authenticated
USING(bucket_id <> 'deckers-creative') WITH CHECK(bucket_id <> 'deckers-creative');
CREATE POLICY "Anyone can view product images"
ON storage.objects FOR SELECT TO PUBLIC
USING (bucket_id = 'Product Images');

DROP POLICY IF EXISTS "Authenticated users can upload product images" ON storage.objects;
CREATE POLICY "Authenticated users can upload product images"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (bucket_id = 'Product Images');

DROP POLICY IF EXISTS "Authenticated users can update product images" ON storage.objects;
CREATE POLICY "Authenticated users can update product images"
ON storage.objects FOR UPDATE TO authenticated
USING (bucket_id = 'Product Images')
WITH CHECK (bucket_id = 'Product Images');

DROP POLICY IF EXISTS "employee files staff insert" ON storage.objects;
CREATE POLICY "employee files staff insert"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'employee files'
    AND public.current_app_user_can_manage_employee_files()
);

DROP POLICY IF EXISTS "employee files staff read" ON storage.objects;
CREATE POLICY "employee files staff read"
ON storage.objects FOR SELECT TO authenticated
USING (
    bucket_id = 'employee files'
    AND public.current_app_user_can_manage_employee_files()
);

DROP POLICY IF EXISTS "employee files staff update" ON storage.objects;
CREATE POLICY "employee files staff update"
ON storage.objects FOR UPDATE TO authenticated
USING (
    bucket_id = 'employee files'
    AND public.current_app_user_can_manage_employee_files()
)
WITH CHECK (
    bucket_id = 'employee files'
    AND public.current_app_user_can_manage_employee_files()
);

DROP POLICY IF EXISTS "smartstock releases admin insert" ON storage.objects;
CREATE POLICY "smartstock releases admin insert"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (
    bucket_id = 'smartstock-releases'
    AND COALESCE(public.current_app_user_is_admin(), false)
);

DROP POLICY IF EXISTS "smartstock releases admin update" ON storage.objects;
CREATE POLICY "smartstock releases admin update"
ON storage.objects FOR UPDATE TO authenticated
USING (
    bucket_id = 'smartstock-releases'
    AND COALESCE(public.current_app_user_is_admin(), false)
)
WITH CHECK (
    bucket_id = 'smartstock-releases'
    AND COALESCE(public.current_app_user_is_admin(), false)
);

DROP POLICY IF EXISTS "smartstock releases authenticated read" ON storage.objects;
CREATE POLICY "smartstock releases authenticated read"
ON storage.objects FOR SELECT TO authenticated
USING (
    bucket_id = 'smartstock-releases'
    AND (
        EXISTS (
            SELECT 1 FROM public.app_releases ar
            WHERE ar.artifact_bucket = storage.objects.bucket_id
              AND ar.artifact_path = storage.objects.name
              AND ar.published = true
        )
        OR COALESCE(public.current_app_user_is_admin(), false)
    )
);

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

-- Store-server-only recovery copy. The project's global Storage limit still applies.
DO $custom_order_storage$ BEGIN
 IF to_regclass('storage.buckets') IS NOT NULL THEN
  INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
   VALUES ('custom-order-private','custom-order-private',FALSE,NULL,
     ARRAY['image/jpeg','image/png','image/webp','image/gif','application/pdf',
       'application/msword','application/vnd.openxmlformats-officedocument.wordprocessingml.document',
       'application/vnd.ms-excel','application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
       'application/vnd.ms-powerpoint','application/vnd.openxmlformats-officedocument.presentationml.presentation'])
   ON CONFLICT(id) DO UPDATE SET public=FALSE,file_size_limit=NULL,
     allowed_mime_types=EXCLUDED.allowed_mime_types;
  DROP POLICY IF EXISTS custom_order_private_server_only ON storage.objects;
  CREATE POLICY custom_order_private_server_only ON storage.objects AS RESTRICTIVE
    FOR ALL TO anon,authenticated
    USING(bucket_id <> 'custom-order-private') WITH CHECK(bucket_id <> 'custom-order-private');
 END IF;
END $custom_order_storage$;
