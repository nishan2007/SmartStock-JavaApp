package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals(9, ServerSupabaseMigrationRunner.migrationResources().size());
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
    }
}
