package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.SQLException;

public final class StoreHydrationService {
    private StoreHydrationService() {
    }

    public static HydrationResult refreshAssignedStores(Connection local, Integer userId) throws SQLException {
        if (userId == null) {
            return HydrationResult.skipped("No signed-in user.");
        }
        return hydrate(local, null, false);
    }

    public static HydrationResult hydrateSelectedStore(Connection local, Integer locationId) throws SQLException {
        if (locationId == null) {
            return HydrationResult.skipped("No store selected.");
        }
        return hydrate(local, locationId, true);
    }

    private static HydrationResult hydrate(Connection local, Integer locationId, boolean includeHistory) throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() == DatabaseMode.CLOUD_DIRECT) {
            return HydrationResult.skipped("Already connected directly to cloud.");
        }
        if (!config.hasCloudConnection()) {
            return HydrationResult.skipped("Cloud database connection is not configured.");
        }

        try (Connection cloud = DB.getCloudConnection()) {
            int rows = ReferenceDataSyncService.pullReferenceData(local, cloud);
            if (includeHistory) {
                rows += ReferenceDataSyncService.pullExistingLocationHistory(local, cloud, locationId);
            }
            return HydrationResult.synced(rows);
        }
    }

    public record HydrationResult(boolean attempted, int rows, String message) {
        public static HydrationResult synced(int rows) {
            return new HydrationResult(true, rows, "Pulled " + rows + " cloud row(s).");
        }

        public static HydrationResult skipped(String message) {
            return new HydrationResult(false, 0, message);
        }
    }
}
