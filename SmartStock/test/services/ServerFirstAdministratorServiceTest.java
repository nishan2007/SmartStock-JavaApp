package services;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
