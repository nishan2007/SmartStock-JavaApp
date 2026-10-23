-- Private authentication state belongs to store servers, never register reference data.
CREATE SCHEMA IF NOT EXISTS customer_auth;
REVOKE ALL ON SCHEMA customer_auth FROM PUBLIC;
CREATE TABLE IF NOT EXISTS customer_auth.identities (
    email_key text PRIMARY KEY CHECK(email_key ~ '^[a-f0-9]{64}$'),
    revision bigint NOT NULL CHECK(revision > 0),
    identity_data jsonb NOT NULL,
    synchronized_at timestamptz NOT NULL DEFAULT now()
);
REVOKE ALL ON ALL TABLES IN SCHEMA customer_auth FROM PUBLIC;
