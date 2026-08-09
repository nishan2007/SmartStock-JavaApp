package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiOnlyCloudSyncBoundaryTest {
    @Test
    void freshBaseSchemaDefinesEmailTriggerFunctionBeforeUsingIt() throws Exception {
        String base = Files.readString(ROOT.resolve("database/base_schema_setup.sql"));
        String returns = Files.readString(ROOT.resolve("database/returns_setup.sql"));
        String rls = Files.readString(ROOT.resolve("database/supabase_rls_hardening_setup.sql"));
        String defaults = Files.readString(ROOT.resolve(
                "database/migrations/20260718185621_normalize_shared_column_defaults.sql"));
        String badgePreference = Files.readString(ROOT.resolve(
                "database/migrations/20260720120000_add_badge_pin_login_preference.sql"));
        String apiExchange = Files.readString(ROOT.resolve(
                "database/migrations/20260723143000_api_only_sync_exchange.sql"));
        int function = base.indexOf("CREATE OR REPLACE FUNCTION set_email_outbox_updated_at()");
        int trigger = base.indexOf("EXECUTE FUNCTION set_email_outbox_updated_at()");

        assertTrue(function >= 0);
        assertTrue(trigger > function);
        assertTrue(base.substring(function, trigger).contains("SET search_path = public"));
        assertFalse(base.contains("ALTER TABLE sale_returns"));
        assertTrue(returns.indexOf("CREATE TABLE IF NOT EXISTS sale_returns")
                < returns.indexOf("ADD COLUMN IF NOT EXISTS cross_store_request_id"));
        assertTrue(rls.contains("to_regclass('public.' || internal_table)"));
        assertTrue(rls.contains("to_regprocedure('public.assign_employee_badge_id()')"));
        assertFalse(rls.contains("REVOKE ALL ON public.sync_locks"));
        assertTrue(defaults.contains("ALTER TABLE IF EXISTS public.wifi_sessions"));
        assertTrue(badgePreference.contains("ALTER TABLE IF EXISTS public.lan_api_sessions"));
        assertTrue(apiExchange.contains("CREATE TABLE IF NOT EXISTS public.sync_outbox"));
        assertTrue(apiExchange.contains("CREATE TABLE IF NOT EXISTS public.sync_applied_events"));
    }

    private static final Path ROOT = Path.of(System.getProperty("user.dir"));

    @Test
    void productionRuntimePathsDoNotOpenCloudJdbcConnections() throws Exception {
        for (String relative : List.of(
                "src/services/SyncWorker.java",
                "src/services/StoreHydrationService.java",
                "src/app/ProductionReadinessMain.java",
                "src/app/ProductionRecoveryDrillMain.java",
                "src/services/ServerImageAssetMaintenance.java")) {
            String source = Files.readString(ROOT.resolve(relative));
            assertFalse(source.contains("getCloudConnection()"), relative);
            assertFalse(source.contains("cloudJdbcUrl()"), relative);
            assertFalse(source.contains("cloudDbPassword()"), relative);
        }
    }

    @Test
    void setupHidesLegacyCloudPostgresCredentials() throws Exception {
        String source = Files.readString(ROOT.resolve("src/ui/screens/DatabaseSetup.java"));
        assertFalse(source.contains("Supabase Database Address"));
        assertFalse(source.contains("Supabase Database User"));
        assertFalse(source.contains("Supabase Database Password"));
        assertFalse(source.contains("!database.hasCloudConnection()"));
    }

    @Test
    void serverOnlyRpcsAreRestrictedAndMirrorExcludesPasswordVerifiers() throws Exception {
        String migration = Files.readString(ROOT.resolve(
                "database/migrations/20260723143000_api_only_sync_exchange.sql"));
        String mirror = Files.readString(ROOT.resolve(
                "src/services/CloudRowMirrorService.java"));

        assertTrue(migration.contains("ALTER TABLE public.smartstock_store_rows ENABLE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("FROM PUBLIC, anon, authenticated"));
        assertTrue(migration.contains("TO service_role"));
        assertTrue(mirror.contains("clean.contains(\"password\")"));
        assertTrue(mirror.contains("clean.contains(\"_pin_\")"));
        assertTrue(mirror.contains("clean.contains(\"secret\")"));
    }

    @Test
    void legacyCleanupIsAllowlistedRecoverableAndGenerationGated() throws Exception {
        String inventory = Files.readString(ROOT.resolve(
                "database/migrations/20260809123000_supabase_legacy_cleanup_inventory.sql"));
        String quarantine = Files.readString(ROOT.resolve(
                "tools/quarantine-supabase-legacy.sql"));
        String fresh = Files.readString(ROOT.resolve(
                "database/migrations/20260809131500_quarantine_empty_fresh_cloud_legacy.sql"));
        String rollback = Files.readString(ROOT.resolve(
                "tools/rollback-supabase-legacy-quarantine.sql"));
        String integrity = Files.readString(ROOT.resolve(
                "database/migrations/20260809140000_legacy_quarantine_integrity_guard.sql"));
        String integrityFix = Files.readString(ROOT.resolve(
                "database/migrations/20260809141500_fix_legacy_quarantine_integrity_guard.sql"));
        String baseline = Files.readString(ROOT.resolve(
                "database/migrations/20260809143000_quarantine_presence_baseline.sql"));
        String wifiRemoval = Files.readString(ROOT.resolve(
                "database/migrations/20260809144500_remove_wifi_sessions_from_supabase.sql"));
        String freshFinal = Files.readString(ROOT.resolve(
                "database/migrations/20260809150000_finalize_fresh_cloud_legacy_quarantine.sql"));

        assertTrue(inventory.contains("LEGACY_CANDIDATE"));
        assertTrue(inventory.contains("assert_legacy_cleanup_ready"));
        assertTrue(quarantine.contains("QUARANTINE SMARTSTOCK LEGACY TABLES"));
        assertTrue(quarantine.contains("SET SCHEMA smartstock_legacy"));
        assertFalse(quarantine.toUpperCase().contains("DROP TABLE"));
        assertTrue(fresh.contains("IF EXISTS (SELECT 1 FROM public.locations)"));
        assertTrue(fresh.contains("disposition = 'LEGACY_CANDIDATE'"));
        assertFalse(fresh.toUpperCase().contains("DROP TABLE"));
        assertTrue(rollback.contains("SET SCHEMA public"));
        assertTrue(rollback.contains("assert_legacy_quarantine_intact(NULL"));
        assertTrue(rollback.contains("v_expected_candidates"));
        assertFalse(rollback.toUpperCase().contains("DROP TABLE"));
        assertTrue(integrity.contains("p_expected_candidates integer DEFAULT 85"));
        assertTrue(integrity.contains("p_minimum_age interval"));
        assertTrue(integrity.contains("Every retained object must exist only in public"));
        assertTrue(integrity.contains("A Supabase API role can still use the quarantine schema"));
        assertFalse(integrity.toUpperCase().contains("DROP TABLE"));
        assertTrue(integrityFix.contains("expected physical quarantined tables"));
        assertTrue(integrityFix.contains("v_manifest_candidates <> 86"));
        assertTrue(integrityFix.contains("balance_sheet_bf_overrides"));
        assertTrue(integrityFix.contains("inventory.object_schema"));
        assertFalse(integrityFix.toUpperCase().contains("DROP TABLE"));
        assertTrue(baseline.contains("quarantine_expected_present"));
        assertTrue(baseline.contains("p_expected_candidates integer DEFAULT NULL"));
        assertTrue(baseline.contains("A baseline-absent legacy table appeared"));
        assertTrue(baseline.contains("Every classified legacy name requires a recorded quarantine baseline"));
        assertFalse(baseline.toUpperCase().contains("DROP TABLE"));
        assertTrue(wifiRemoval.contains("DROP TABLE IF EXISTS public.wifi_sessions"));
        assertFalse(wifiRemoval.toUpperCase().contains("CASCADE;"));
        assertTrue(wifiRemoval.contains("table_name = 'wifi_sessions'"));
        assertTrue(freshFinal.contains("assert_legacy_quarantine_intact"));
        assertTrue(freshFinal.contains("quarantine_expected_present"));
    }

    @Test
    void protectedCredentialRecoveryIsServiceRoleOnlyAndFreshnessGated() throws Exception {
        String migration = Files.readString(ROOT.resolve(
                "database/migrations/20260809134500_protected_user_credential_recovery.sql"));
        String recovery = Files.readString(ROOT.resolve(
                "src/services/CloudRecoveryService.java"));

        assertTrue(migration.contains("SECURITY INVOKER"));
        assertTrue(migration.contains("FROM PUBLIC, anon, authenticated"));
        assertTrue(migration.contains("TO service_role"));
        assertTrue(migration.contains("credentials_verified_at"));
        assertTrue(migration.contains("current_generation_id = p_generation_id"));
        assertTrue(recovery.contains("smartstock_store_user_credentials"));
        assertTrue(recovery.contains("verifyProtectedCredentialRow"));
    }

    @Test
    void protectedCredentialsUsePrivateGenerationVaultInsteadOfPublicUsers()
            throws Exception {
        String vault = Files.readString(ROOT.resolve(
                "database/migrations/20260809151500_private_user_credential_vault.sql"));
        String mirror = Files.readString(ROOT.resolve(
                "src/services/CloudRowMirrorService.java"));
        String recovery = Files.readString(ROOT.resolve(
                "src/services/CloudRecoveryService.java"));
        String referenceSync = Files.readString(ROOT.resolve(
                "src/services/ReferenceDataSyncService.java"));

        assertTrue(vault.contains("smartstock_private.store_user_credentials"));
        assertTrue(vault.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(vault.contains("NEW.password_hash := NULL"));
        assertTrue(vault.contains("NEW.badge_secret_hash := NULL"));
        assertTrue(vault.contains("NEW.employee_pin_hash := NULL"));
        assertTrue(vault.contains("smartstock_store_user_credentials"));
        assertTrue(vault.contains("REVOKE ALL"));
        assertTrue(mirror.contains("badge_secret_salt"));
        assertTrue(mirror.contains("badge_secret_hash"));
        assertTrue(recovery.contains("badge_secret_salt=?"));
        assertTrue(recovery.contains("badge_secret_hash=?"));
        assertTrue(referenceSync.contains("PROTECTED_USER_CLOUD_COLUMNS"));
    }
}
