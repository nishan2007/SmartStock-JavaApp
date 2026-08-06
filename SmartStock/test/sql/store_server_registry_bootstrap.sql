\set ON_ERROR_STOP on

DO $bootstrap$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated NOLOGIN; END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN CREATE ROLE service_role NOLOGIN; END IF;
END
$bootstrap$;

CREATE TABLE public.locations (
    location_id integer PRIMARY KEY,
    name text NOT NULL,
    receipt_store_code text NOT NULL DEFAULT '0001',
    timezone text NOT NULL DEFAULT 'America/New_York',
    address text
);
INSERT INTO public.locations(location_id,name) VALUES
  (1,'Registry Test Store'),(2,'Emergency Test Store'),
  (3,'Concurrency Test Store'),(4,'Rollback Test Store');
UPDATE public.locations SET receipt_store_code=lpad(location_id::text,4,'0');
