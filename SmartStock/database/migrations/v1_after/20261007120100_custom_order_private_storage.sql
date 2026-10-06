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
