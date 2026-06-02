package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.sql.SQLException;

public final class OfflineWriteGuard {
    private OfflineWriteGuard() {
    }

    public static void requireCloudForGlobalWrite(String actionName) throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() == DatabaseMode.CLOUD_DIRECT) {
            return;
        }
        if (config.mode() == DatabaseMode.CLIENT) {
            throw new SQLException(actionName + " changes shared setup data and must be done from server/cloud-direct mode while cloud is reachable.");
        }
        try (Connection ignored = DB.getCloudConnection()) {
            // Connection success is enough to allow the shared/admin write.
        }
    }
}
