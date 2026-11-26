CREATE TABLE IF NOT EXISTS public.customer_account_web_requests (
    browser_id uuid NOT NULL,
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    request_key uuid NOT NULL,
    request_hash text NOT NULL,
    response jsonb,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (browser_id,user_id,location_id,request_key)
);
CREATE INDEX IF NOT EXISTS customer_account_web_requests_created_idx
    ON public.customer_account_web_requests(created_at);
ALTER TABLE public.customer_account_web_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.customer_account_web_requests FROM PUBLIC;

CREATE INDEX IF NOT EXISTS sync_cross_store_source_order_lines_idx
    ON public.sync_cross_store_source_rows (source_location_id, table_name, (row_data->>'custom_order_id'))
    WHERE table_name IN ('custom_order_lines','custom_order_payments','custom_order_audit_log',
        'custom_order_line_deliveries','custom_order_line_production_history','custom_order_line_returns');
