package services;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudRowMirrorServiceTest {
    @Test
    void excludesOnlyDerivedDeviceActivityFromDurableMirror() {
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "last_seen"));
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "updated_at"));
        assertTrue(CloudRowMirrorService.excludedOperationalColumn("devices", "session_count"));

        assertFalse(CloudRowMirrorService.excludedOperationalColumn("devices", "is_approved"));
        assertFalse(CloudRowMirrorService.excludedOperationalColumn("devices", "last_store_id"));
        assertFalse(CloudRowMirrorService.excludedOperationalColumn(
                "customer_account_transactions", "updated_at"));
    }

    @Test
    void excludesEveryCredentialVerifierFromGeneralMirrorRows() {
        assertTrue(CloudRowMirrorService.sensitiveColumn("users", "password_hash"));
        assertTrue(CloudRowMirrorService.sensitiveColumn("users", "employee_pin_salt"));
        assertTrue(CloudRowMirrorService.sensitiveColumn("users", "employee_pin_hash"));
        assertTrue(CloudRowMirrorService.sensitiveColumn("users", "badge_secret_salt"));
        assertTrue(CloudRowMirrorService.sensitiveColumn("users", "badge_secret_hash"));
        assertFalse(CloudRowMirrorService.sensitiveColumn("users", "badge_id"));
    }

    @Test
    void scopesStoreOwnedAndDependentRowsAndFailsClosedForUnknownTables() throws Exception {
        assertTrue(CloudRowMirrorService.scopePredicate("sales", true)
                .contains("location_id=?"));
        assertTrue(CloudRowMirrorService.scopePredicate("sale_items", false)
                .contains("FROM sales"));
        assertTrue(CloudRowMirrorService.scopePredicate("custom_order_lines", false)
                .contains("FROM custom_orders"));
        assertTrue(CloudRowMirrorService.scopePredicate("devices", false)
                .contains("FROM device_sessions"));
        assertTrue(CloudRowMirrorService.scopePredicate("receiving_batches", true)
                .contains("FROM store_transfers"));
        String auditScope = CloudRowMirrorService.scopePredicate(
                "security_audit_events", false);
        assertTrue(auditScope.contains("t.device_id IS NOT NULL"));
        assertTrue(auditScope.contains("t.device_id IS NULL"));
        assertTrue(CloudRowMirrorService.scopePredicate("products", false).equals("TRUE"));
        assertThrows(java.sql.SQLException.class,
                () -> CloudRowMirrorService.scopePredicate("unclassified_table", false));
    }

    @Test
    void everyMirrorTableHasAnExplicitOwnershipRule() throws Exception {
        Set<String> directLocationTables = Set.of(
                "balance_sheet_bf_overrides", "balance_sheet_submission_revisions",
                "balance_sheet_submissions", "bank_transactions",
                "cash_drawer_device_assignments", "cash_drawer_handovers",
                "cash_drawer_sessions", "cash_drawers", "change_basket_updates",
                "cheque_bank_deposits", "company_customization",
                "custom_order_item_movements", "custom_orders",
                "customer_account_transactions", "email_outbox",
                "employee_payroll_bonuses", "employee_schedule_assignments",
                "employee_schedule_shifts", "employee_time_clock", "expenses",
                "held_carts", "inventory", "inventory_movements",
                "invoice_delivery_events", "invoice_payments", "invoices",
                "maintenance_machines", "other_income_entries", "payroll_payments",
                "product_shelf_assignments", "quotations", "receiving_batches",
                "sale_audit_log", "sale_returns", "sales", "shelf_locations",
                "user_locations"
        );
        for (String table : ReferenceDataSyncService.cloudMirrorTablesForApi()) {
            CloudRowMirrorService.scopePredicate(table, directLocationTables.contains(table));
        }
    }
}
