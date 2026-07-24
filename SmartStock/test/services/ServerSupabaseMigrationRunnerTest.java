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
    void everyMigrationIsPackagedAndBootstrapIsLast() {
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().size() > 60);
        for (String resource : ServerSupabaseMigrationRunner.migrationResources()) {
            assertDoesNotThrow(() -> SqlScriptRunner.readResource(resource), resource);
        }
        assertTrue(ServerSupabaseMigrationRunner.migrationResources().get(
                        ServerSupabaseMigrationRunner.migrationResources().size() - 1)
                .endsWith("first_admin_bootstrap.sql"));
    }
}
