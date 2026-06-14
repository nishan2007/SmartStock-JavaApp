-- SmartStock app update release channel.
-- Run this against the live Supabase database/storage project.

CREATE TABLE IF NOT EXISTS app_releases (
    release_id BIGSERIAL PRIMARY KEY,
    version TEXT NOT NULL,
    build_number INTEGER NOT NULL,
    platform TEXT NOT NULL DEFAULT 'windows',
    artifact_bucket TEXT NOT NULL DEFAULT 'smartstock-releases',
    artifact_path TEXT NOT NULL,
    sha256 TEXT NOT NULL,
    file_size_bytes BIGINT,
    release_notes TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    minimum_supported_version TEXT,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER REFERENCES users(user_id),
    CONSTRAINT app_releases_platform_check CHECK (platform IN ('windows', 'mac', 'linux', 'all')),
    CONSTRAINT app_releases_sha256_check CHECK (sha256 ~* '^[a-f0-9]{64}$'),
    CONSTRAINT app_releases_published_at_check CHECK (
        (published = FALSE) OR (published_at IS NOT NULL)
    )
);

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS artifact_bucket TEXT NOT NULL DEFAULT 'smartstock-releases';

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS minimum_supported_version TEXT;

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ;

ALTER TABLE app_releases
ADD COLUMN IF NOT EXISTS created_by_user_id INTEGER REFERENCES users(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS app_releases_platform_build_idx
ON app_releases(platform, build_number);

CREATE INDEX IF NOT EXISTS app_releases_latest_published_idx
ON app_releases(platform, build_number DESC)
WHERE published = TRUE;

ALTER TABLE app_releases ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    has_admin_function BOOLEAN := to_regprocedure('public.current_app_user_is_admin()') IS NOT NULL;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        REVOKE ALL ON TABLE public.app_releases FROM anon;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        GRANT SELECT ON TABLE public.app_releases TO authenticated;
        GRANT USAGE, SELECT ON SEQUENCE public.app_releases_release_id_seq TO authenticated;
        DROP POLICY IF EXISTS app_releases_authenticated_published_read ON public.app_releases;
        CREATE POLICY app_releases_authenticated_published_read
        ON public.app_releases
        FOR SELECT
        TO authenticated
        USING (published = TRUE);

        IF has_admin_function THEN
            DROP POLICY IF EXISTS app_releases_authenticated_admin_write ON public.app_releases;
            EXECUTE '
                CREATE POLICY app_releases_authenticated_admin_write
                ON public.app_releases
                FOR ALL
                TO authenticated
                USING (COALESCE(public.current_app_user_is_admin(), FALSE))
                WITH CHECK (COALESCE(public.current_app_user_is_admin(), FALSE))
            ';
        END IF;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
        GRANT ALL ON TABLE public.app_releases TO service_role;
        GRANT USAGE, SELECT ON SEQUENCE public.app_releases_release_id_seq TO service_role;
        DROP POLICY IF EXISTS app_releases_service_role_all ON public.app_releases;
        CREATE POLICY app_releases_service_role_all
        ON public.app_releases
        FOR ALL
        TO service_role
        USING (TRUE)
        WITH CHECK (TRUE);
    END IF;
END $$;

INSERT INTO permissions (permission_key, permission_name)
SELECT 'APP_UPDATES', 'App Updates'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE UPPER(permission_key) = 'APP_UPDATES'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM roles r
JOIN permissions p ON UPPER(p.permission_key) = 'APP_UPDATES'
WHERE UPPER(r.role_name) = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp
      WHERE rp.role_id = r.role_id
        AND rp.permission_id = p.permission_id
  );

DO $$
DECLARE
    has_admin_function BOOLEAN := to_regprocedure('public.current_app_user_is_admin()') IS NOT NULL;
BEGIN
    IF to_regclass('storage.buckets') IS NULL OR to_regclass('storage.objects') IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO storage.buckets (id, name, public)
    VALUES ('smartstock-releases', 'smartstock-releases', FALSE)
    ON CONFLICT (id) DO UPDATE
    SET public = FALSE;

    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        IF NOT EXISTS (
            SELECT 1
            FROM pg_policies
            WHERE schemaname = 'storage'
              AND tablename = 'objects'
              AND policyname = 'smartstock releases authenticated read'
        ) THEN
            IF has_admin_function THEN
                EXECUTE '
                    CREATE POLICY "smartstock releases authenticated read"
                      ON storage.objects FOR SELECT
                      TO authenticated
                      USING (
                          bucket_id = ''smartstock-releases''
                          AND (
                              EXISTS (
                                  SELECT 1
                                  FROM public.app_releases ar
                                  WHERE ar.artifact_bucket = storage.objects.bucket_id
                                    AND ar.artifact_path = storage.objects.name
                                    AND ar.published = TRUE
                              )
                              OR COALESCE(public.current_app_user_is_admin(), FALSE)
                          )
                      )
                ';
            ELSE
                EXECUTE '
                    CREATE POLICY "smartstock releases authenticated read"
                      ON storage.objects FOR SELECT
                      TO authenticated
                      USING (
                          bucket_id = ''smartstock-releases''
                          AND EXISTS (
                              SELECT 1
                              FROM public.app_releases ar
                              WHERE ar.artifact_bucket = storage.objects.bucket_id
                                AND ar.artifact_path = storage.objects.name
                                AND ar.published = TRUE
                          )
                      )
                ';
            END IF;
        END IF;

        IF has_admin_function AND NOT EXISTS (
            SELECT 1
            FROM pg_policies
            WHERE schemaname = 'storage'
              AND tablename = 'objects'
              AND policyname = 'smartstock releases admin insert'
        ) THEN
            EXECUTE '
                CREATE POLICY "smartstock releases admin insert"
                  ON storage.objects FOR INSERT
                  TO authenticated
                  WITH CHECK (
                      bucket_id = ''smartstock-releases''
                      AND COALESCE(public.current_app_user_is_admin(), FALSE)
                  )
            ';
        END IF;

        IF has_admin_function AND NOT EXISTS (
            SELECT 1
            FROM pg_policies
            WHERE schemaname = 'storage'
              AND tablename = 'objects'
              AND policyname = 'smartstock releases admin update'
        ) THEN
            EXECUTE '
                CREATE POLICY "smartstock releases admin update"
                  ON storage.objects FOR UPDATE
                  TO authenticated
                  USING (
                      bucket_id = ''smartstock-releases''
                      AND COALESCE(public.current_app_user_is_admin(), FALSE)
                  )
                  WITH CHECK (
                      bucket_id = ''smartstock-releases''
                      AND COALESCE(public.current_app_user_is_admin(), FALSE)
                  )
            ';
        END IF;
    END IF;
END $$;
