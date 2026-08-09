INSERT INTO storage.buckets(id, name, public, file_size_limit, allowed_mime_types)
VALUES
    ('employee files', 'employee files', false, NULL, NULL),
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
