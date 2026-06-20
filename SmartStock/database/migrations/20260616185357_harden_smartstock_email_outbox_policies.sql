ALTER FUNCTION set_email_outbox_updated_at() SET search_path = public;

ALTER TABLE email_outbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE email_outbox_events ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS email_outbox_service_role_all ON email_outbox;
CREATE POLICY email_outbox_service_role_all
ON email_outbox
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);

DROP POLICY IF EXISTS email_outbox_events_service_role_all ON email_outbox_events;
CREATE POLICY email_outbox_events_service_role_all
ON email_outbox_events
FOR ALL
TO service_role
USING (true)
WITH CHECK (true);
