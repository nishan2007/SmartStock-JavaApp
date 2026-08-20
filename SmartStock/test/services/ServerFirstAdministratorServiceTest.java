package services;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ServerFirstAdministratorServiceTest {
    @Test
    void transferredAdminPreservesBadgeIdentityExactly() {
        Timestamp generated = Timestamp.valueOf("2026-07-20 10:15:00");
        var source = new ProductionIdentityMigrationService.SourceIdentity(
                42, "owner", "owner@example.com", "Store Owner", "Boss",
                Date.valueOf("1980-02-03"), "Ab-001", "salt", "hash",
                generated, "11111111-1111-4111-8111-111111111111",
                true, "ADMIN");
        var identity = ServerFirstAdministratorService.transferred(source);
        assertEquals("Ab-001", identity.badgeId());
        assertEquals("salt", identity.badgeSecretSalt());
        assertEquals("hash", identity.badgeSecretHash());
        assertEquals(generated, identity.badgeGeneratedAt());
        assertTrue(identity.transferred());
    }

    @Test
    void transferredAdminPreservesVerifierWhenDateOfBirthIsAbsent() {
        Timestamp generated = Timestamp.valueOf("2026-07-20 10:15:00");
        var source = new ProductionIdentityMigrationService.SourceIdentity(
                43, "owner2", "owner2@example.com", "Store Owner 2", null,
                null, "Ab-002", "salt2", "hash2", generated, null,
                true, "ADMIN");

        var identity = ServerFirstAdministratorService.transferred(source);

        assertEquals(null, identity.dateOfBirth());
        assertEquals("salt2", identity.badgeSecretSalt());
        assertEquals("hash2", identity.badgeSecretHash());
    }

    @Test
    void transferRejectsInactiveOrNonAdminAndNewAdminHasNoBadge() {
        var manager = new ProductionIdentityMigrationService.SourceIdentity(
                5, "manager", "manager@example.com", "Manager", null,
                null, null, null, null, null, null, true, "MANAGER");
        assertThrows(IllegalArgumentException.class,
                () -> ServerFirstAdministratorService.transferred(manager));

        var created = ServerFirstAdministratorService.newAdministrator(
                "owner", "owner@example.com", "Store Owner");
        assertFalse(created.transferred());
        assertEquals(null, created.badgeId());
    }

    @Test
    void requiresCompleteBadgeVerifierAndValidEmail() {
        assertThrows(IllegalArgumentException.class, () ->
                new ServerFirstAdministratorService.Identity(
                        "owner", "owner@example.com", "Owner", null, null,
                        "ABC", "salt", null, null, true));
        assertThrows(IllegalArgumentException.class, () ->
                ServerFirstAdministratorService.newAdministrator(
                        "owner", "not-an-email", "Owner"));
    }

    @Test
    void developmentTransferCanRecoverFromSharedRolePasswordRotation() throws Exception {
        String service = Files.readString(Path.of(
                "src/services/ServerFirstAdministratorService.java"));
        String dialog = Files.readString(Path.of(
                "src/ui/screens/FirstAdministratorSetupDialog.java"));

        assertTrue(service.contains("\"28P01\".equals(developmentFailure.getSQLState())"));
        assertTrue(service.contains("production.secretKey(\"primary-db-password\")"));
        assertTrue(service.contains("!user.equals(currentUser)"));
        assertTrue(dialog.contains("development.jdbcUrl()"));
        assertTrue(dialog.contains("development.user()"));
    }

    @Test
    void configuredDevelopmentAdministratorsCanBeReadWhenExplicitlyEnabled() throws Exception {
        assumeTrue(Boolean.getBoolean("smartstock.test.readConfiguredDevelopmentAdmins"));
        assertFalse(ServerFirstAdministratorService.listDevelopmentAdministrators().isEmpty());
    }

    @Test
    void firstAdministratorResumeStateIsPartOfTheLocalSchemaContract() throws Exception {
        String contract = Files.readString(Path.of("src/services/SchemaContractService.java"));
        String migration = Files.readString(Path.of(
                "database/migrations/v1_after/20260820170000_first_admin_setup_state.sql"));

        assertTrue(contract.contains("20260820170000_first_admin_setup_state.sql"));
        assertTrue(contract.indexOf("ensureFirstAdminSetupUpgrade(connection)")
                < contract.indexOf("VALIDATED_LOCAL_DATABASES.contains(key)"));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS public.smartstock_first_admin_setup"));
        assertTrue(migration.contains("auth_user_id uuid NOT NULL"));
        assertTrue(migration.contains("REVOKE ALL"));
    }
}
