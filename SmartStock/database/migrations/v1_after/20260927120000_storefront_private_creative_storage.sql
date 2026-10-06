-- Private artwork and project covers are uploaded only by the store server.
DO $creative_storage$ BEGIN
 IF to_regclass('storage.buckets') IS NOT NULL THEN
  INSERT INTO storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
   VALUES('deckers-creative','deckers-creative',FALSE,8388608,
          ARRAY['image/jpeg','image/png','application/pdf','application/octet-stream'])
   ON CONFLICT(id) DO UPDATE SET public=FALSE,file_size_limit=8388608,allowed_mime_types=EXCLUDED.allowed_mime_types;
  DROP POLICY IF EXISTS deckers_creative_server_only ON storage.objects;
  CREATE POLICY deckers_creative_server_only ON storage.objects AS RESTRICTIVE FOR ALL TO anon,authenticated
   USING(bucket_id <> 'deckers-creative') WITH CHECK(bucket_id <> 'deckers-creative');
 END IF;
END $creative_storage$;
