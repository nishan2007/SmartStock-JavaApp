package services;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionIdentityMigrationServiceTest {
    @Test
    void acceptsOneToThreeUniqueUsersAndAuthIds() {
        var valid = new ProductionIdentityMigrationService.MigrationManifest(
                "Main Store",
                List.of(
                        user("one", "11111111-1111-4111-8111-111111111111"),
                        user("two", "22222222-2222-4222-8222-222222222222"),
                        user("three", "33333333-3333-4333-8333-333333333333")
                ));
        assertDoesNotThrow(() -> ProductionIdentityMigrationService.validateManifest(valid));

        assertDoesNotThrow(() -> ProductionIdentityMigrationService.validateManifest(
                new ProductionIdentityMigrationService.MigrationManifest(
                        "Main Store", valid.users().subList(0, 2))));
        assertDoesNotThrow(() -> ProductionIdentityMigrationService.validateManifest(
                new ProductionIdentityMigrationService.MigrationManifest(
                        "Main Store", valid.users().subList(0, 1))));
        assertThrows(IllegalArgumentException.class, () ->
                ProductionIdentityMigrationService.validateManifest(
                        new ProductionIdentityMigrationService.MigrationManifest(
                                "Main Store", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> ProductionIdentityMigrationService.validateManifest(
                        new ProductionIdentityMigrationService.MigrationManifest(
                                "Main Store", List.of(valid.users().get(0),
                                valid.users().get(0), valid.users().get(2)))));
    }

    @Test
    void rejectsMalformedAuthUuidAndMissingLocation() {
        assertThrows(IllegalArgumentException.class,
                () -> ProductionIdentityMigrationService.validateManifest(
                        new ProductionIdentityMigrationService.MigrationManifest(
                                "", List.of(
                                user("one", "not-a-uuid"),
                                user("two", "22222222-2222-4222-8222-222222222222"),
                                user("three", "33333333-3333-4333-8333-333333333333")))));
    }

    private static ProductionIdentityMigrationService.UserMapping user(String username,
                                                                       String authId) {
        return new ProductionIdentityMigrationService.UserMapping(username, authId);
    }
}
