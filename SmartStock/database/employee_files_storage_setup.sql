-- Employee file storage for employee photos and ID-card documents.
-- Run this against the Supabase cloud database where the storage schema exists.

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS employee_id_card_document_url TEXT;

INSERT INTO storage.buckets (id, name, public)
VALUES ('employee files', 'employee files', FALSE)
ON CONFLICT (id) DO UPDATE
SET name = EXCLUDED.name,
    public = FALSE;

CREATE OR REPLACE FUNCTION public.current_app_user_can_manage_employee_files()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path TO ''
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.users u
    LEFT JOIN public.role_permissions rp ON rp.role_id = u.role_id
    LEFT JOIN public.permissions p ON p.permission_id = rp.permission_id
    WHERE u.auth_user_id = auth.uid()
      AND COALESCE(u.is_active, true) = true
      AND (
        u.role_id = 1
        OR UPPER(COALESCE(p.permission_key, '')) IN ('EMPLOYEE_MANAGEMENT', 'COMPANY_PREFERENCES')
      )
  )
$$;

REVOKE ALL ON FUNCTION public.current_app_user_can_manage_employee_files() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_app_user_can_manage_employee_files() TO authenticated, service_role;

DO $$
BEGIN
  DROP POLICY IF EXISTS "employee files authenticated read" ON storage.objects;
  DROP POLICY IF EXISTS "employee files authenticated insert" ON storage.objects;
  DROP POLICY IF EXISTS "employee files authenticated update" ON storage.objects;
  DROP POLICY IF EXISTS "employee files staff read" ON storage.objects;
  DROP POLICY IF EXISTS "employee files staff insert" ON storage.objects;
  DROP POLICY IF EXISTS "employee files staff update" ON storage.objects;

  CREATE POLICY "employee files staff read"
    ON storage.objects FOR SELECT
    TO authenticated
    USING (
      bucket_id = 'employee files'
      AND public.current_app_user_can_manage_employee_files()
    );

  CREATE POLICY "employee files staff insert"
    ON storage.objects FOR INSERT
    TO authenticated
    WITH CHECK (
      bucket_id = 'employee files'
      AND public.current_app_user_can_manage_employee_files()
    );

  CREATE POLICY "employee files staff update"
    ON storage.objects FOR UPDATE
    TO authenticated
    USING (
      bucket_id = 'employee files'
      AND public.current_app_user_can_manage_employee_files()
    )
    WITH CHECK (
      bucket_id = 'employee files'
      AND public.current_app_user_can_manage_employee_files()
    );
END $$;
