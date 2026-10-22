-- Private organization intake; never mirror these tables to registers/cloud.
CREATE TABLE IF NOT EXISTS studio_template_submission_links (
 location_id integer NOT NULL, template_id uuid NOT NULL, token_hash text NOT NULL UNIQUE, link_seed uuid,
 enabled boolean NOT NULL DEFAULT true, code_hash text, salt text,
 generation integer NOT NULL DEFAULT 1, failures integer NOT NULL DEFAULT 0,
 locked_until timestamptz, updated_by integer, updated_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(location_id,template_id), CHECK ((code_hash IS NULL) = (salt IS NULL)), CHECK (generation>0 AND failures>=0)
);
CREATE TABLE IF NOT EXISTS studio_template_submission_sessions (
 session_hash text PRIMARY KEY, location_id integer NOT NULL, template_id uuid NOT NULL,
 generation integer NOT NULL, expires_at timestamptz NOT NULL,
 FOREIGN KEY(location_id,template_id) REFERENCES studio_template_submission_links(location_id,template_id)
);
CREATE INDEX IF NOT EXISTS studio_submission_sessions_expiry_idx ON studio_template_submission_sessions(expires_at);
CREATE INDEX IF NOT EXISTS studio_submission_sessions_template_idx ON studio_template_submission_sessions(location_id,template_id,generation);
CREATE TABLE IF NOT EXISTS studio_template_submissions (
 location_id integer NOT NULL, template_id uuid NOT NULL, submission_id uuid NOT NULL,
 schema_hash text NOT NULL, payload jsonb NOT NULL, request_hash text NOT NULL,
 received_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
 state text NOT NULL DEFAULT 'PENDING' CHECK(state IN ('PENDING','IN_REVIEW','PRINTING','JOB_SENT','REJECTED')),
 claimed_by integer, claim_token uuid, claim_until timestamptz, record_id uuid, note text NOT NULL DEFAULT '',
 PRIMARY KEY(location_id,template_id,submission_id)
);
CREATE INDEX IF NOT EXISTS studio_submissions_queue_idx ON studio_template_submissions(location_id,template_id,received_at DESC,submission_id);
CREATE INDEX IF NOT EXISTS studio_submissions_pending_idx ON studio_template_submissions(location_id,template_id,state);
REVOKE ALL ON studio_template_submission_links,studio_template_submission_sessions,studio_template_submissions FROM PUBLIC;
