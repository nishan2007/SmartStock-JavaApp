package services;

import managers.SessionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical recovery-table manifest and local tombstone writer.
 *
 * <p>Pre-v1 direct PostgreSQL-to-PostgreSQL copying and schema repair were
 * intentionally removed. Runtime cloud exchange is owned by the authenticated
 * HTTPS queue/mirror services, while the lists below define complete recovery
 * snapshot coverage.</p>
 */
public final class ReferenceDataSyncService {
    private static final List<String> TABLES = List.of(
            "roles",
            "permissions",
            "role_permissions",
            "mobile_permissions",
            "role_mobile_permissions",
            "company_info",
            "locations",
            "company_customization",
            "image_cloud_configuration",
            "image_assets",
            "image_asset_references",
            "users",
            "user_locations",
            "employee_wallet_credentials",
            "time_clock_auto_close_settings",
            "employee_payroll_settings",
            "employee_schedule_shifts",
            "employee_schedule_holidays",
            "employee_schedule_assignments",
            "employee_time_clock",
            "employee_time_clock_adjustments",
            "devices",
            "register_transfers",
            "device_sessions",
            "categories",
            "item_types",
            "item_brands",
            "shelf_locations",
            "vendors",
            "products",
            "product_shelf_assignments",
            "product_barcodes",
            "inventory",
            "customer_accounts",
            "cash_drawers",
            "cash_drawer_device_assignments",
            "change_basket_updates",
            "email_outbox",
            "email_outbox_events"
    );
    private static final List<String> CLOUD_PULL_ORDER = List.of(
            "roles",
            "permissions",
            "mobile_permissions",
            "role_permissions",
            "role_mobile_permissions",
            "company_info",
            "locations",
            "company_customization",
            "image_cloud_configuration",
            "image_assets",
            "image_asset_references",
            "customer_types",
            "users",
            "user_locations",
            "employee_wallet_credentials",
            "time_clock_auto_close_settings",
            "employee_payroll_settings",
            "employee_schedule_shifts",
            "employee_schedule_holidays",
            "employee_schedule_assignments",
            "devices",
            "register_transfers",
            "device_sessions",
            "categories",
            "item_types",
            "item_brands",
            "shelf_locations",
            "vendors",
            "products",
            "product_shelf_assignments",
            "product_barcodes",
            "inventory",
            "customer_accounts",
            "cash_drawers",
            "cash_drawer_device_assignments",
            "cash_drawer_sessions",
            "cash_drawer_handovers",
            "change_basket_updates",
            "receiving_batches",
            "sales",
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "custom_order_items",
            "custom_order_item_barcodes",
            "custom_order_item_variants",
            "custom_order_item_variant_barcodes",
            "custom_order_print_materials",
            "custom_order_print_size_presets",
            "custom_order_design_placements",
            "custom_orders",
            "custom_order_lines",
            "custom_order_line_print_addons",
            "custom_order_payments",
            "custom_order_inventory_reservations",
            "custom_order_status_history",
            "custom_order_line_deliveries",
            "custom_order_line_production_history",
            "custom_order_line_returns",
            "custom_order_item_movements",
            "custom_order_audit_log",
            "quotations",
            "quotation_lines",
            "quotation_status_history",
            "quotation_audit_log",
            "invoices",
            "invoice_lines",
            "invoice_payments",
            "invoice_delivery_events",
            "invoice_delivery_lines",
            "invoice_status_history",
            "invoice_audit_log",
            "inventory_movements",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "balance_sheet_bf_overrides",
            "balance_sheet_submissions",
            "balance_sheet_submission_revisions",
            "cheque_bank_deposits",
            "expenses",
            "other_income_entries",
            "employee_time_clock",
            "employee_time_clock_adjustments",
            "employee_payroll_bonuses",
            "payroll_payments",
            "bank_transactions",
            "held_carts",
            "held_cart_items",
            "store_transfers",
            "store_transfer_items",
            "maintenance_machines",
            "maintenance_parts",
            "maintenance_machine_parts",
            "maintenance_tickets",
            "email_outbox",
            "email_outbox_events"
    );
    private static final List<String> LOCAL_PUSH_ORDER = List.of(
            "roles",
            "permissions",
            "mobile_permissions",
            "role_permissions",
            "role_mobile_permissions",
            "company_info",
            "locations",
            "company_customization",
            "image_cloud_configuration",
            "image_assets",
            "image_asset_references",
            "users",
            "user_locations",
            "employee_wallet_credentials",
            "time_clock_auto_close_settings",
            "employee_payroll_settings",
            "employee_schedule_shifts",
            "employee_schedule_holidays",
            "employee_schedule_assignments",
            "device_sessions",
            "cash_drawers",
            "cash_drawer_device_assignments",
            "customer_accounts",
            "cash_drawer_sessions",
            "cash_drawer_handovers",
            "change_basket_updates",
            "categories",
            "item_types",
            "item_brands",
            "shelf_locations",
            "products",
            "product_shelf_assignments",
            "product_barcodes",
            "receiving_batches",
            "sales",
            "sale_items",
            "sale_returns",
            "sale_return_items",
            "sale_audit_log",
            "inventory",
            "custom_order_items",
            "custom_order_item_barcodes",
            "custom_order_item_variants",
            "custom_order_item_variant_barcodes",
            "custom_order_print_materials",
            "custom_order_print_size_presets",
            "custom_order_design_placements",
            "custom_orders",
            "custom_order_lines",
            "custom_order_line_print_addons",
            "custom_order_payments",
            "custom_order_inventory_reservations",
            "custom_order_status_history",
            "custom_order_line_deliveries",
            "custom_order_line_production_history",
            "custom_order_line_returns",
            "custom_order_item_movements",
            "custom_order_audit_log",
            "quotations",
            "quotation_lines",
            "quotation_status_history",
            "quotation_audit_log",
            "invoices",
            "invoice_lines",
            "invoice_payments",
            "invoice_delivery_events",
            "invoice_delivery_lines",
            "invoice_status_history",
            "invoice_audit_log",
            "inventory_movements",
            "customer_account_transactions",
            "customer_account_payment_allocations",
            "balance_sheet_bf_overrides",
            "balance_sheet_submissions",
            "balance_sheet_submission_revisions",
            "cheque_bank_deposits",
            "expenses",
            "other_income_entries",
            "employee_time_clock",
            "employee_time_clock_adjustments",
            "employee_payroll_bonuses",
            "payroll_payments",
            "bank_transactions",
            "held_carts",
            "held_cart_items",
            "store_transfers",
            "store_transfer_items",
            "maintenance_machines",
            "maintenance_parts",
            "maintenance_machine_parts",
            "maintenance_tickets",
            "email_outbox",
            "email_outbox_events"
    );

    private ReferenceDataSyncService() {
    }

    static List<String> cloudPullOrderForApi() {
        return CLOUD_PULL_ORDER;
    }

    static List<String> cloudMirrorTablesForApi() {
        LinkedHashMap<String, Boolean> tables = new LinkedHashMap<>();
        CLOUD_PULL_ORDER.forEach(table -> tables.put(table, Boolean.TRUE));
        LOCAL_PUSH_ORDER.forEach(table -> tables.put(table, Boolean.TRUE));
        TABLES.forEach(table -> tables.put(table, Boolean.TRUE));
        List.of("cross_store_refund_requests", "cross_store_refund_lines",
                "cross_store_refund_reconciliation", "security_audit_events", "register_transfers")
                .forEach(table -> tables.put(table, Boolean.TRUE));
        return List.copyOf(tables.keySet());
    }

    public static void recordTombstone(Connection connection, String tableName,
                                       Map<String, ?> keyData) throws SQLException {
        if (connection == null || tableName == null || tableName.isBlank()
                || keyData == null || keyData.isEmpty()) {
            return;
        }
        SchemaContractService.requireLocalReady(connection);
        String sql = """
                INSERT INTO sync_tombstones (table_name, key_data, origin_device_id)
                VALUES (?, ?::jsonb, ?)
                ON CONFLICT (table_name, key_data)
                DO UPDATE SET
                    deleted_at = GREATEST(sync_tombstones.deleted_at, EXCLUDED.deleted_at),
                    origin_device_id = COALESCE(EXCLUDED.origin_device_id,
                                                sync_tombstones.origin_device_id)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            statement.setString(2, SyncJson.object(keyData));
            statement.setString(3, currentDeviceId());
            statement.executeUpdate();
        }
    }

    private static String currentDeviceId() {
        try {
            String deviceId = SessionManager.getCurrentDeviceId();
            return deviceId == null || deviceId.isBlank() ? null : deviceId;
        } catch (Exception ignored) {
            return null;
        }
    }
}
