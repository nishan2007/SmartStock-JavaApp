ALTER TABLE public.company_customization
 ADD COLUMN IF NOT EXISTS custom_order_file_limit_bytes bigint NOT NULL DEFAULT 104857600
 CHECK (custom_order_file_limit_bytes > 0);

CREATE TABLE IF NOT EXISTS public.custom_order_files (
 file_id uuid PRIMARY KEY,
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 custom_order_line_id bigint NOT NULL REFERENCES public.custom_order_lines(custom_order_line_id),
 filename text NOT NULL,
 content_type text NOT NULL,
 byte_size bigint NOT NULL CHECK (byte_size > 0),
 sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
 storage_key text NOT NULL UNIQUE,
 cloud_status text NOT NULL DEFAULT 'PENDING' CHECK (cloud_status IN ('PENDING','PRESENT','ERROR')),
 cloud_error text,
 uploaded_by_user_id integer,
 uploaded_by_customer boolean NOT NULL DEFAULT false,
 override_by_user_id integer,
 override_reason text,
 created_at timestamptz NOT NULL DEFAULT now(),
 removed_at timestamptz,
 removed_by_user_id integer,
 deleted_at timestamptz
);
CREATE INDEX IF NOT EXISTS custom_order_files_active_line
 ON public.custom_order_files(custom_order_line_id,created_at)
 WHERE removed_at IS NULL AND deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS public.custom_order_access_links (
 link_id uuid PRIMARY KEY,
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 token_sha256 text NOT NULL UNIQUE,
 created_by_user_id integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 revoked_at timestamptz
);

CREATE TABLE IF NOT EXISTS public.custom_order_design_proofs (
 proof_id uuid PRIMARY KEY,
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 custom_order_line_id bigint NOT NULL REFERENCES public.custom_order_lines(custom_order_line_id),
 revision integer NOT NULL CHECK (revision > 0),
 filename text NOT NULL,
 content_type text NOT NULL CHECK (content_type IN ('image/jpeg','image/png','application/pdf')),
 byte_size bigint NOT NULL CHECK (byte_size > 0),
 sha256 text NOT NULL CHECK (sha256 ~ '^[0-9a-f]{64}$'),
 storage_key text NOT NULL UNIQUE,
 cloud_status text NOT NULL DEFAULT 'PENDING' CHECK (cloud_status IN ('PENDING','PRESENT','ERROR')),
 cloud_error text,
 token_sha256 text NOT NULL UNIQUE,
 status text NOT NULL DEFAULT 'AWAITING_APPROVAL'
   CHECK (status IN ('AWAITING_APPROVAL','APPROVED','CHANGES_REQUESTED','SUPERSEDED')),
 created_by_user_id integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 decided_at timestamptz,
 feedback text,
 revoked_at timestamptz,
 deleted_at timestamptz,
 UNIQUE(custom_order_line_id,revision)
);
CREATE UNIQUE INDEX IF NOT EXISTS custom_order_design_proofs_pending
 ON public.custom_order_design_proofs(custom_order_line_id)
 WHERE status='AWAITING_APPROVAL';
CREATE INDEX IF NOT EXISTS custom_order_design_proofs_order
 ON public.custom_order_design_proofs(custom_order_id,custom_order_line_id,revision DESC);

CREATE TABLE IF NOT EXISTS public.custom_order_file_uploads (
 upload_id uuid PRIMARY KEY,
 custom_order_id bigint NOT NULL REFERENCES public.custom_orders(custom_order_id),
 custom_order_line_id bigint NOT NULL REFERENCES public.custom_order_lines(custom_order_line_id),
 kind text NOT NULL CHECK (kind IN ('ATTACHMENT','PROOF')),
 filename text NOT NULL,
 content_type text NOT NULL,
 expected_bytes bigint NOT NULL CHECK (expected_bytes > 0),
 received_bytes bigint NOT NULL DEFAULT 0,
 sha256 text NOT NULL,
 customer_link_id uuid REFERENCES public.custom_order_access_links(link_id),
 staff_user_id integer,
 override_by_user_id integer,
 override_reason text,
 created_at timestamptz NOT NULL DEFAULT now(),
 expires_at timestamptz NOT NULL DEFAULT (now()+interval '24 hours')
);

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('REMOVE_CUSTOM_ORDER_FILES','Remove Custom Order Files',
 'Allows removing attachments from undelivered custom orders.','Custom Orders','Orders')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES ('CUSTOM_ORDER_FILE_SIZE_OVERRIDE','Override Custom Order File Size',
 'Allows manager approval for a custom order file above the company size limit.','Custom Orders','Orders')
ON CONFLICT(permission_key) DO NOTHING;
INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key IN ('REMOVE_CUSTOM_ORDER_FILES','CUSTOM_ORDER_FILE_SIZE_OVERRIDE')
ON CONFLICT(role_id,permission_id) DO NOTHING;
