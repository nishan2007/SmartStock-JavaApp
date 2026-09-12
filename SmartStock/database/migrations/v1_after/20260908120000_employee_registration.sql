-- Store-owned onboarding; intentionally excluded from cross-store reference exchange.
CREATE TABLE IF NOT EXISTS employee_registration_runtime (
    runtime_id INTEGER PRIMARY KEY CHECK (runtime_id=1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    changed_by INTEGER REFERENCES users(user_id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO employee_registration_runtime(runtime_id) VALUES(1) ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS employee_registrations (
    registration_id UUID PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    status TEXT NOT NULL CHECK(status IN ('SUBMITTING','PENDING','APPROVING','APPROVED','REJECTED')),
    first_name TEXT NOT NULL,
    middle_name TEXT NOT NULL DEFAULT '',
    last_name TEXT NOT NULL,
    nickname TEXT NOT NULL DEFAULT '',
    email TEXT NOT NULL,
    phone TEXT NOT NULL,
    date_of_birth DATE NOT NULL,
    submission_fingerprint TEXT NOT NULL,
    document_url TEXT,
    auth_user_id UUID UNIQUE,
    employee_id INTEGER UNIQUE REFERENCES users(user_id),
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by INTEGER REFERENCES users(user_id),
    reviewed_at TIMESTAMPTZ,
    rejection_reason TEXT,
    CHECK(status='SUBMITTING' OR (document_url IS NOT NULL AND auth_user_id IS NOT NULL)),
    CHECK(status NOT IN ('APPROVING','APPROVED') OR employee_id IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS employee_registrations_email_idx ON employee_registrations(LOWER(email));
CREATE INDEX IF NOT EXISTS employee_registrations_status_date_idx ON employee_registrations(status,submitted_at DESC,registration_id);
CREATE TABLE IF NOT EXISTS employee_registration_reviews (
    review_id BIGSERIAL PRIMARY KEY,
    registration_id UUID NOT NULL REFERENCES employee_registrations(registration_id),
    actor_id INTEGER NOT NULL REFERENCES users(user_id),
    action TEXT NOT NULL,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS employee_registration_reviews_registration_idx ON employee_registration_reviews(registration_id,created_at);

-- Also covers generic employee updates: a pending identity cannot be activated elsewhere.
CREATE OR REPLACE FUNCTION enforce_employee_registration_activation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.is_active AND EXISTS (
        SELECT 1 FROM employee_registrations r
        WHERE (r.auth_user_id=NEW.auth_user_id OR r.employee_id=NEW.user_id OR LOWER(r.email)=LOWER(NEW.email))
          AND r.status<>'APPROVED'
    ) THEN
        RAISE EXCEPTION 'Approve the pending employee registration before activating this account';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS employee_registration_activation_guard ON users;
CREATE TRIGGER employee_registration_activation_guard BEFORE INSERT OR UPDATE OF is_active,auth_user_id,email ON users
FOR EACH ROW EXECUTE FUNCTION enforce_employee_registration_activation();

-- Cloud copies are recovery data, never browser-accessible onboarding tables.
ALTER TABLE employee_registrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_registration_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE employee_registration_runtime ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON employee_registrations,employee_registration_reviews,employee_registration_runtime FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
        REVOKE ALL ON employee_registrations,employee_registration_reviews,employee_registration_runtime FROM anon;
    END IF;
    IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
        REVOKE ALL ON employee_registrations,employee_registration_reviews,employee_registration_runtime FROM authenticated;
    END IF;
END $$;
