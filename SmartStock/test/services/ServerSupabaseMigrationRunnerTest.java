package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSupabaseMigrationRunnerTest {
    private static final String REF = "abcdefghijklmnopqrst";
    private static final SupabaseProjectConfig PROJECT = new SupabaseProjectConfig(
            SupabaseProjectConfig.Environment.PRODUCTION,
            "https://" + REF + ".supabase.co",
            "sb_publishable_test",
            REF);

    @Test
    void acceptsDirectAndSessionPoolerConnectionsWithoutEmbeddedPassword() {
        var direct = ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                "postgresql://postgres:[YOUR-PASSWORD]@db." + REF
                        + ".supabase.co:5432/postgres", PROJECT);
        assertEquals("postgres", direct.username());
        assertEquals(5432, direct.port());

        var pooler = ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                "postgresql://postgres." + REF
                        + "@aws-0-us-east-1.pooler.supabase.com:5432/postgres", PROJECT);
        assertEquals("postgres." + REF, pooler.username());
    }

    @Test
    void rejectsWrongProjectEmbeddedPasswordTransactionPoolerAndWrongDatabase() {
        assertThrows(IllegalArgumentException.class, () ->
                ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                        "postgresql://postgres:real-secret@db." + REF
                                + ".supabase.co:5432/postgres", PROJECT));
        assertThrows(IllegalArgumentException.class, () ->
                ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                        "postgresql://postgres@db.zzzzzzzzzzzzzzzzzzzz.supabase.co:5432/postgres",
                        PROJECT));
        assertThrows(IllegalArgumentException.class, () ->
                ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                        "postgresql://postgres." + REF
                                + "@aws-0-us-east-1.pooler.supabase.com:6543/postgres", PROJECT));
        assertThrows(IllegalArgumentException.class, () ->
                ServerSupabaseMigrationRunner.ConnectionSpec.parse(
                        "postgresql://postgres@db." + REF
                                + ".supabase.co:5432/smartstock", PROJECT));
    }

    @Test
    void v1ManifestContainsOnlyTheCanonicalBaselineAndImmutablePostV1Chain() {
        assertEquals(28, ServerSupabaseMigrationRunner.migrationResources().size());
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains("database/migrations/v1_after/20261008190000_manual_custom_order_entry.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains("database/migrations/v1_after/20261003173203_cross_store_egress_cloud.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains("database/migrations/v1_after/20261008170100_custom_order_spoil_permissions.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains("database/migrations/v1_after/20261008160000_studio_device_access.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20261008150000_guard_payroll_event_ownership.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20261008140000_store_mirror_clone_timeout.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20261008130000_fast_cloud_sync_schema_status.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20261007120100_custom_order_private_storage.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260927120000_storefront_private_creative_storage.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260919180000_name_custom_item_permission.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260909120000_employment_portal.sql"));
        assertFalse(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260831150000_wallet_template.sql"));
        assertFalse(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260904150000_cash_drawer_count_history.sql"));
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260902180000_wallet_location_relevance.sql"));
        assertFalse(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260903120000_whatsapp_sales_documents.sql"));
        assertFalse(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260907120000_customer_charge_authorizations.sql"));
        assertFalse(ServerSupabaseMigrationRunner.migrationResources().contains(
                "database/migrations/v1_after/20260911180000_sale_quick_pick_item_types.sql"));
        for (String resource : ServerSupabaseMigrationRunner.migrationResources()) {
            assertDoesNotThrow(() -> SqlScriptRunner.readResource(resource), resource);
        }
        assertEquals("database/v1/cloud/001_schema.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(0));
        assertEquals("database/v1/cloud/003_metadata.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(2));
        assertEquals(
                "database/migrations/v1_after/20260809190000_revoke_anon_security_definer_execute.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(3));
        assertEquals(
                "database/migrations/v1_after/20260809192551_restrict_service_only_rpc_execute.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(4));
        assertEquals(
                "database/migrations/v1_after/20260809211000_cloud_return_receipt_numbers.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(5));
        assertEquals(
                "database/migrations/v1_after/20260811190000_add_register_transfers.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(6));
        assertEquals(
                "database/migrations/v1_after/20260811190100_secure_cloud_register_transfers.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(7));
        assertEquals(
                "database/migrations/v1_after/20260811233100_route_store_transfer_receipts.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(8));
        assertEquals(
                "database/migrations/v1_after/20260819120000_onedrive_image_provider.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(9));
        assertEquals(
                "database/migrations/v1_after/20260819230000_onedrive_shared_identifiers.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(10));
        assertEquals(
                "database/migrations/v1_after/20260820030000_bound_store_snapshot_retention.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(11));
        assertEquals(
                "database/migrations/v1_after/20260820213000_seed_cloud_builtin_roles.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(12));
        assertEquals(
                "database/migrations/v1_after/20260820220000_complete_builtin_permissions.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(13));
        assertEquals(
                "database/migrations/v1_after/20260831120000_apple_wallet_badges.sql",
                ServerSupabaseMigrationRunner.migrationResources().get(14));
    }

    @Test
    void recognizesOnlyTheRecordedHistoricalPermissionChecksum() {
        String resource = "database/migrations/v1_after/20260820220000_complete_builtin_permissions.sql";
        String packaged = "a9aa3656cbee7d49e96e9a86e6698e4d392b130eec0ee11c4029f94b0d77c8cb";
        String historical = "1deeb7f89f9964a161ecee3faf02f8d783e6a7b69652bc6a6bcf5a62834948f7";
        assertTrue(ServerSupabaseMigrationRunner.matchesAppliedChecksum(resource, packaged, historical));
        assertFalse(ServerSupabaseMigrationRunner.matchesAppliedChecksum(resource, packaged,
                "0000000000000000000000000000000000000000000000000000000000000000"));
        assertFalse(ServerSupabaseMigrationRunner.matchesAppliedChecksum(resource,
                "0000000000000000000000000000000000000000000000000000000000000000",
                historical));
        assertFalse(ServerSupabaseMigrationRunner.matchesAppliedChecksum(
                "database/migrations/v1_after/20260831120000_apple_wallet_badges.sql",
                packaged, historical));
    }

    @Test
    void syncSchemaPreflightDoesNotCountRecoveryRows() throws Exception {
        String sql = SqlScriptRunner.readResource(
                "database/migrations/v1_after/20261008130000_fast_cloud_sync_schema_status.sql");
        assertTrue(sql.contains("smartstock_private.smartstock_schema_metadata"));
        assertTrue(sql.contains("REVOKE ALL ON FUNCTION public.smartstock_sync_schema_status() FROM PUBLIC"));
        assertTrue(sql.contains("GRANT EXECUTE ON FUNCTION public.smartstock_sync_schema_status() TO service_role"));
        assertTrue(!sql.toLowerCase().contains("count(*)"));
    }
}
