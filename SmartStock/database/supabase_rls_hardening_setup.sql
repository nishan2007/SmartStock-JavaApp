-- SmartStock hosted Supabase Data API hardening.
-- Requires public.current_app_user_has_location(integer) from supabase_rpc_security_setup.sql.

DO $$
BEGIN
    IF to_regprocedure('public.current_app_user_has_location(integer)') IS NULL THEN
        RAISE EXCEPTION 'Run supabase_rpc_security_setup.sql before Supabase RLS hardening';
    END IF;
END $$;

ALTER TABLE public.cash_drawer_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.cash_drawer_handovers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.change_basket_updates ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quotations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quotation_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quotation_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.quotation_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_delivery_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_delivery_lines ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.invoice_audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS cash_drawer_sessions_authenticated_all ON public.cash_drawer_sessions;
DROP POLICY IF EXISTS cash_drawer_sessions_location_access ON public.cash_drawer_sessions;
CREATE POLICY cash_drawer_sessions_location_access ON public.cash_drawer_sessions
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS cash_drawer_handovers_authenticated_all ON public.cash_drawer_handovers;
DROP POLICY IF EXISTS cash_drawer_handovers_location_access ON public.cash_drawer_handovers;
CREATE POLICY cash_drawer_handovers_location_access ON public.cash_drawer_handovers
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS change_basket_updates_authenticated_all ON public.change_basket_updates;
DROP POLICY IF EXISTS change_basket_updates_location_access ON public.change_basket_updates;
CREATE POLICY change_basket_updates_location_access ON public.change_basket_updates
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS quotations_authenticated_all ON public.quotations;
DROP POLICY IF EXISTS sales_quotes_authenticated_all ON public.quotations;
DROP POLICY IF EXISTS quotations_location_access ON public.quotations;
CREATE POLICY quotations_location_access ON public.quotations
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS quotation_lines_authenticated_all ON public.quotation_lines;
DROP POLICY IF EXISTS sales_quote_lines_authenticated_all ON public.quotation_lines;
DROP POLICY IF EXISTS quotation_lines_location_access ON public.quotation_lines;
CREATE POLICY quotation_lines_location_access ON public.quotation_lines
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.quotations q
    WHERE q.quotation_id = quotation_lines.quotation_id
      AND (SELECT public.current_app_user_has_location(q.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.quotations q
    WHERE q.quotation_id = quotation_lines.quotation_id
      AND (SELECT public.current_app_user_has_location(q.location_id))
));

DROP POLICY IF EXISTS quotation_status_history_authenticated_all ON public.quotation_status_history;
DROP POLICY IF EXISTS sales_quote_status_history_authenticated_all ON public.quotation_status_history;
DROP POLICY IF EXISTS quotation_status_history_location_access ON public.quotation_status_history;
CREATE POLICY quotation_status_history_location_access ON public.quotation_status_history
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.quotations q
    WHERE q.quotation_id = quotation_status_history.quotation_id
      AND (SELECT public.current_app_user_has_location(q.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.quotations q
    WHERE q.quotation_id = quotation_status_history.quotation_id
      AND (SELECT public.current_app_user_has_location(q.location_id))
));

DROP POLICY IF EXISTS quotation_audit_log_authenticated_all ON public.quotation_audit_log;
DROP POLICY IF EXISTS sales_quote_audit_log_authenticated_all ON public.quotation_audit_log;
DROP POLICY IF EXISTS quotation_audit_log_location_read ON public.quotation_audit_log;
CREATE POLICY quotation_audit_log_location_read ON public.quotation_audit_log
FOR SELECT TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.quotations q
    WHERE q.quotation_id = quotation_audit_log.quotation_id
      AND (SELECT public.current_app_user_has_location(q.location_id))
));
REVOKE INSERT, UPDATE, DELETE ON public.quotation_audit_log FROM authenticated;

DROP POLICY IF EXISTS invoices_authenticated_all ON public.invoices;
DROP POLICY IF EXISTS sales_orders_authenticated_all ON public.invoices;
DROP POLICY IF EXISTS invoices_location_access ON public.invoices;
CREATE POLICY invoices_location_access ON public.invoices
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS invoice_lines_authenticated_all ON public.invoice_lines;
DROP POLICY IF EXISTS sales_order_lines_authenticated_all ON public.invoice_lines;
DROP POLICY IF EXISTS invoice_lines_location_access ON public.invoice_lines;
CREATE POLICY invoice_lines_location_access ON public.invoice_lines
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_lines.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_lines.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
));

DROP POLICY IF EXISTS invoice_payments_authenticated_all ON public.invoice_payments;
DROP POLICY IF EXISTS sales_order_payments_authenticated_all ON public.invoice_payments;
DROP POLICY IF EXISTS invoice_payments_location_access ON public.invoice_payments;
CREATE POLICY invoice_payments_location_access ON public.invoice_payments
FOR ALL TO authenticated
USING ((SELECT public.current_app_user_has_location(location_id)))
WITH CHECK ((SELECT public.current_app_user_has_location(location_id)));

DROP POLICY IF EXISTS invoice_delivery_events_authenticated_all ON public.invoice_delivery_events;
DROP POLICY IF EXISTS sales_order_delivery_events_authenticated_all ON public.invoice_delivery_events;
DROP POLICY IF EXISTS invoice_delivery_events_location_access ON public.invoice_delivery_events;
CREATE POLICY invoice_delivery_events_location_access ON public.invoice_delivery_events
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_delivery_events.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_delivery_events.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
));

DROP POLICY IF EXISTS invoice_delivery_lines_authenticated_all ON public.invoice_delivery_lines;
DROP POLICY IF EXISTS sales_order_delivery_lines_authenticated_all ON public.invoice_delivery_lines;
DROP POLICY IF EXISTS invoice_delivery_lines_location_access ON public.invoice_delivery_lines;
CREATE POLICY invoice_delivery_lines_location_access ON public.invoice_delivery_lines
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1
    FROM public.invoice_delivery_events e
    JOIN public.invoices i ON i.invoice_id = e.invoice_id
    WHERE e.invoice_delivery_event_id = invoice_delivery_lines.invoice_delivery_event_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1
    FROM public.invoice_delivery_events e
    JOIN public.invoices i ON i.invoice_id = e.invoice_id
    WHERE e.invoice_delivery_event_id = invoice_delivery_lines.invoice_delivery_event_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
));

DROP POLICY IF EXISTS invoice_status_history_authenticated_all ON public.invoice_status_history;
DROP POLICY IF EXISTS sales_order_status_history_authenticated_all ON public.invoice_status_history;
DROP POLICY IF EXISTS invoice_status_history_location_access ON public.invoice_status_history;
CREATE POLICY invoice_status_history_location_access ON public.invoice_status_history
FOR ALL TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_status_history.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
))
WITH CHECK (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_status_history.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
));

DROP POLICY IF EXISTS invoice_audit_log_authenticated_all ON public.invoice_audit_log;
DROP POLICY IF EXISTS sales_order_audit_log_authenticated_all ON public.invoice_audit_log;
DROP POLICY IF EXISTS invoice_audit_log_location_read ON public.invoice_audit_log;
CREATE POLICY invoice_audit_log_location_read ON public.invoice_audit_log
FOR SELECT TO authenticated
USING (EXISTS (
    SELECT 1 FROM public.invoices i
    WHERE i.invoice_id = invoice_audit_log.invoice_id
      AND (SELECT public.current_app_user_has_location(i.location_id))
));
REVOKE INSERT, UPDATE, DELETE ON public.invoice_audit_log FROM authenticated;

-- Device rows are exposed only through validated RPCs, never as a directly writable table.
REVOKE ALL ON public.devices, public.device_sessions FROM anon, authenticated;
REVOKE ALL ON public.sync_locks, public.sync_service_status, public.wifi_sessions FROM anon, authenticated;

REVOKE ALL ON FUNCTION public.assign_employee_badge_id() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.generate_employee_badge_id() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.current_app_user_can_return_at_location(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.current_app_user_can_return_at_location(integer) TO authenticated, service_role;
ALTER FUNCTION public.assign_employee_badge_id() SET search_path TO '';
ALTER FUNCTION public.generate_employee_badge_id() SET search_path TO '';
ALTER FUNCTION public.current_app_user_can_return_at_location(integer) SET search_path TO '';

-- Public function execution is opt-in. Explicit grants in supabase_rpc_security_setup.sql remain.
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
