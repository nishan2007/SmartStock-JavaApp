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
    void everyMigrationIsPackagedAndRecoveryGenerationMigrationIsLast() {
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().size() > 60);
        for (String resource : ServerSupabaseMigrationRunner.migrationResources()) {
            assertDoesNotThrow(() -> SqlScriptRunner.readResource(resource), resource);
        }
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().get(
                        ServerSupabaseMigrationRunner.migrationResources().size() - 1)
                .endsWith("private_user_credential_vault.sql"));
    }

    @Test
    void optionalWifiDefaultFixKeepsThePreviouslyDeployedChecksumCompatible()
            throws Exception {
        var method = ServerSupabaseMigrationRunner.class.getDeclaredMethod(
                "acceptedChecksum", String.class, String.class, String.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(null,
                "database/migrations/20260718185621_normalize_shared_column_defaults.sql",
                "new-checksum",
                "6f5169134ffb4aea54ca01a1353b9bbe3a00a7a52591f0d7fa77dc1dc9749a49"));
        assertEquals(false, method.invoke(null,
                "database/migrations/another.sql", "new-checksum", "old-checksum"));
        assertEquals(true, method.invoke(null,
                "database/migrations/20260720120000_add_badge_pin_login_preference.sql",
                "new-checksum",
                "25bfe7458164c8d85ff0937c624ff0b2842fb631f0f459490f7b2f45c8432da8"));
        assertEquals(true, method.invoke(null,
                "database/migrations/20260723143000_api_only_sync_exchange.sql",
                "new-checksum",
                "d041832288b45d0610de1ade67b310ecaab47e1043bcd56fa6984f1c81ce4ef4"));
    }
}
