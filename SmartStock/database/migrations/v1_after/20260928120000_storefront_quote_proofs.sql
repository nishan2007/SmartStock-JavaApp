CREATE TABLE IF NOT EXISTS storefront.quote_proofs (
 proof_id uuid PRIMARY KEY,
 request_id uuid NOT NULL REFERENCES storefront.quote_requests(request_id),
 revision integer NOT NULL CHECK(revision > 0),
 asset_reference text NOT NULL,
 original_filename text NOT NULL CHECK(char_length(original_filename) BETWEEN 1 AND 180),
 content_type text NOT NULL CHECK(content_type IN ('image/jpeg','image/png','application/pdf')),
 byte_size integer NOT NULL CHECK(byte_size BETWEEN 1 AND 4194304),
 sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 status text NOT NULL DEFAULT 'AWAITING_APPROVAL' CHECK(status IN ('AWAITING_APPROVAL','APPROVED','CHANGES_REQUESTED','SUPERSEDED')),
 published_by integer NOT NULL,
 created_at timestamptz NOT NULL DEFAULT now(),
 decided_at timestamptz,
 UNIQUE(request_id,revision)
);
CREATE UNIQUE INDEX IF NOT EXISTS storefront_quote_proofs_pending ON storefront.quote_proofs(request_id) WHERE status='AWAITING_APPROVAL';
CREATE INDEX IF NOT EXISTS storefront_quote_proofs_customer ON storefront.quote_proofs(request_id,revision DESC);
CREATE TABLE IF NOT EXISTS storefront.quote_proof_events (
 event_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 proof_id uuid NOT NULL REFERENCES storefront.quote_proofs(proof_id),
 action text NOT NULL CHECK(action IN ('PUBLISHED','APPROVED','CHANGES_REQUESTED')),
 auth_id uuid,
 staff_user_id integer,
 note text NOT NULL DEFAULT '' CHECK(char_length(note)<=2000),
 created_at timestamptz NOT NULL DEFAULT now(),
 CHECK ((action='PUBLISHED' AND staff_user_id IS NOT NULL AND auth_id IS NULL) OR (action<>'PUBLISHED' AND auth_id IS NOT NULL AND staff_user_id IS NULL))
);
CREATE INDEX IF NOT EXISTS storefront_quote_proof_events_proof ON storefront.quote_proof_events(proof_id,created_at);
ALTER TABLE storefront.quote_requests DROP CONSTRAINT IF EXISTS quote_requests_status_check;
ALTER TABLE storefront.quote_requests ADD CONSTRAINT quote_requests_status_check CHECK(status IN ('REQUESTED','REVIEWING','AWAITING_ARTWORK','QUOTED','AWAITING_APPROVAL','APPROVED','CHANGES_REQUESTED','IN_PRODUCTION','READY','COMPLETED','CANCELLED'));
INSERT INTO storefront.schema_version(version) VALUES(7) ON CONFLICT DO NOTHING;
