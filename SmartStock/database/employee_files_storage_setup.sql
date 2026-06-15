-- Employee file storage for ID-card documents.
-- Run this against the Supabase cloud database where the storage schema exists.

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS employee_id_card_document_url TEXT;

INSERT INTO storage.buckets (id, name, public)
VALUES ('employee files', 'employee files', FALSE)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    public = FALSE;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'employee files authenticated read'
  ) THEN
    CREATE POLICY "employee files authenticated read"
      ON storage.objects FOR SELECT
      TO authenticated
      USING (bucket_id = 'employee files');
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'employee files authenticated insert'
  ) THEN
    CREATE POLICY "employee files authenticated insert"
      ON storage.objects FOR INSERT
      TO authenticated
      WITH CHECK (bucket_id = 'employee files');
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'employee files authenticated update'
  ) THEN
    CREATE POLICY "employee files authenticated update"
      ON storage.objects FOR UPDATE
      TO authenticated
      USING (bucket_id = 'employee files')
      WITH CHECK (bucket_id = 'employee files');
  END IF;
END $$;
