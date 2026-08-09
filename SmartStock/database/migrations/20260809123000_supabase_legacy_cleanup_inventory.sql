-- Explicit allowlist for dependency-first Supabase legacy cleanup.
-- This migration classifies objects only. It does not move or delete data.

CREATE TABLE IF NOT EXISTS smartstock_private.cloud_object_manifest (
    object_type text NOT NULL CHECK (object_type IN ('TABLE')),
    object_name text NOT NULL CHECK (object_name ~ '^[a-z][a-z0-9_]{0,100}$'),
    disposition text NOT NULL CHECK (disposition IN ('RETAIN', 'LEGACY_CANDIDATE')),
    rationale text NOT NULL,
    first_observed_at timestamptz NOT NULL DEFAULT pg_catalog.now(),
    last_verified_at timestamptz,
    quarantine_started_at timestamptz,
    PRIMARY KEY (object_type, object_name)
);

REVOKE ALL ON TABLE smartstock_private.cloud_object_manifest
    FROM PUBLIC, anon, authenticated;

INSERT INTO smartstock_private.cloud_object_manifest(
    object_type, object_name, disposition, rationale
)
SELECT 'TABLE', name, 'RETAIN', rationale
FROM (VALUES
    ('app_releases', 'Authenticated release catalog; artifacts are stored separately.'),
    ('locations', 'Store identity and authorization directory.'),
    ('roles', 'Authorization directory.'),
    ('permissions', 'Authorization directory.'),
    ('role_permissions', 'Authorization directory.'),
    ('mobile_permissions', 'Authorization directory.'),
    ('role_mobile_permissions', 'Authorization directory.'),
    ('users', 'Auth-linked employee identity directory.'),
    ('user_locations', 'Store authorization assignments.'),
    ('devices', 'Device authorization and revocation directory.'),
    ('device_sessions', 'Device security session audit.'),
    ('email_outbox', 'Cloud email delivery queue.'),
    ('email_outbox_events', 'Cloud email delivery audit.'),
    ('image_assets', 'Storage object registry.'),
    ('image_asset_references', 'Storage reference registry.'),
    ('sync_outbox', 'Cross-store event transport.'),
    ('sync_applied_events', 'Idempotent event acknowledgement ledger.'),
    ('store_sync_status', 'Store synchronization health.'),
    ('remote_admin_commands', 'Queued Remote Admin control plane.'),
    ('smartstock_store_rows', 'Mutable current mirror used by synchronization workflows.'),
    ('smartstock_store_mirror_status', 'Pointer to the current verified recovery generation.'),
    ('smartstock_store_snapshot_generations', 'Immutable recovery generation catalog.'),
    ('smartstock_store_snapshot_rows', 'Immutable recovery rows.'),
    ('store_server_instances', 'Primary and standby server registry.'),
    ('store_server_handoffs', 'Verified server handoff workflow.'),
    ('store_server_events', 'Server lifecycle audit.'),
    ('smartstock_cross_store_refund_requests', 'Cross-store refund command queue.'),
    ('smartstock_cross_store_refund_lines', 'Cross-store refund command details.')
) retained(name, rationale)
ON CONFLICT (object_type, object_name) DO UPDATE
SET disposition = EXCLUDED.disposition, rationale = EXCLUDED.rationale;

INSERT INTO smartstock_private.cloud_object_manifest(
    object_type, object_name, disposition, rationale
)
SELECT 'TABLE', name, 'LEGACY_CANDIDATE',
       'Local PostgreSQL is authoritative; verified recovery uses immutable store snapshots.'
FROM unnest(ARRAY[
    'balance_sheet_bf_overrides', 'balance_sheet_submission_revisions',
    'balance_sheet_submissions', 'bank_transactions',
    'cash_drawer_device_assignments', 'cash_drawer_handovers',
    'cash_drawer_sessions', 'cash_drawers', 'categories',
    'change_basket_updates', 'cheque_bank_deposits', 'company_customization',
    'company_info', 'custom_order_audit_log', 'custom_order_design_placements',
    'custom_order_inventory_reservations', 'custom_order_item_barcodes',
    'custom_order_item_movements', 'custom_order_item_variants',
    'custom_order_items', 'custom_order_line_deliveries',
    'custom_order_line_print_addons', 'custom_order_line_production_history',
    'custom_order_line_returns', 'custom_order_lines', 'custom_order_payments',
    'custom_order_print_materials', 'custom_order_print_size_presets',
    'custom_order_status_history', 'custom_orders',
    'customer_account_payment_allocations', 'customer_account_transactions',
    'customer_accounts', 'customer_types', 'employee_payroll_bonuses',
    'employee_payroll_settings', 'employee_schedule_assignments',
    'employee_schedule_holidays', 'employee_schedule_shifts',
    'employee_time_clock', 'employee_time_clock_adjustments', 'expenses',
    'held_cart_items', 'held_carts', 'inventory', 'inventory_movements',
    'invoice_audit_log', 'invoice_delivery_events', 'invoice_delivery_lines',
    'invoice_lines', 'invoice_payments', 'invoice_status_history', 'invoices',
    'item_brands', 'item_types', 'maintenance_machine_parts',
    'maintenance_machines', 'maintenance_parts', 'maintenance_tickets',
    'notification_user_state', 'other_income_entries', 'payroll_payments',
    'product_barcodes', 'product_shelf_assignments', 'products',
    'quotation_audit_log', 'quotation_lines', 'quotation_status_history',
    'quotations', 'receiving_batches', 'sale_audit_log', 'sale_items',
    'sale_return_items', 'sale_returns', 'sales', 'shelf_locations',
    'store_transfer_items', 'store_transfers', 'sync_audit_log',
    'sync_conflicts', 'sync_id_map', 'sync_locks', 'sync_service_status',
    'sync_tombstones', 'time_clock_auto_close_settings', 'vendors',
    'wifi_sessions'
]::text[]) candidate(name)
ON CONFLICT (object_type, object_name) DO UPDATE
SET disposition = EXCLUDED.disposition, rationale = EXCLUDED.rationale;

CREATE OR REPLACE VIEW smartstock_private.cloud_object_inventory
WITH (security_invoker = true)
AS
SELECT manifest.object_type,
       manifest.object_name,
       manifest.disposition,
       manifest.rationale,
       manifest.last_verified_at,
       manifest.quarantine_started_at,
       table_object.oid IS NOT NULL AS object_exists,
       COALESCE(stats.n_live_tup, 0)::bigint AS estimated_rows,
       CASE WHEN table_object.oid IS NULL THEN 0
            ELSE pg_catalog.pg_total_relation_size(table_object.oid) END AS total_bytes
FROM smartstock_private.cloud_object_manifest manifest
LEFT JOIN pg_catalog.pg_namespace namespace_object
  ON namespace_object.nspname = 'public'
LEFT JOIN pg_catalog.pg_class table_object
  ON table_object.relnamespace = namespace_object.oid
 AND table_object.relname = manifest.object_name
 AND table_object.relkind IN ('r', 'p')
LEFT JOIN pg_catalog.pg_stat_user_tables stats
  ON stats.relid = table_object.oid;

REVOKE ALL ON smartstock_private.cloud_object_inventory
    FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION smartstock_private.assert_legacy_cleanup_ready(
    p_max_snapshot_age interval DEFAULT interval '15 minutes'
)
RETURNS void
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path TO ''
AS $$
BEGIN
    IF p_max_snapshot_age IS NULL OR p_max_snapshot_age <= interval '0 seconds' THEN
        RAISE EXCEPTION 'A positive maximum snapshot age is required.';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.smartstock_store_snapshot_generations
        WHERE status = 'BUILDING'
    ) THEN
        RAISE EXCEPTION 'A store recovery generation is still being built.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.locations) THEN
        RAISE EXCEPTION 'No store locations are registered.';
    END IF;
    IF EXISTS (
        SELECT 1
        FROM public.locations location
        LEFT JOIN public.smartstock_store_mirror_status status
          ON status.location_id = location.location_id
        LEFT JOIN public.smartstock_store_snapshot_generations generation
          ON generation.generation_id = status.current_generation_id
         AND generation.status = 'COMPLETE'
        WHERE generation.generation_id IS NULL
           OR generation.completed_at < pg_catalog.now() - p_max_snapshot_age
           OR generation.active_row_count <> status.active_row_count
           OR generation.table_counts <> status.table_counts
    ) THEN
        RAISE EXCEPTION 'Every store requires a recent, completed, count-matched recovery generation.';
    END IF;
END
$$;

REVOKE ALL ON FUNCTION smartstock_private.assert_legacy_cleanup_ready(interval)
    FROM PUBLIC, anon, authenticated;

