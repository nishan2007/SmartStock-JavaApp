package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;

/** Headless, server-only image registry reconciliation for provisioning and operations. */
public final class ServerImageAssetMaintenance {
    private ServerImageAssetMaintenance() { }

    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER) {
            throw new IllegalStateException("Image maintenance can run only on the SmartStock server.");
        }
        try (Connection conn = DB.getConnection()) {
            ServerImageAssetService.ensureSchema(conn);
            ServerImageAssetService.SyncResult result = ServerImageAssetService.synchronize(conn, true);
            ServerImageAssetService.Counts counts = ServerImageAssetService.counts(conn);
            int metadataRows = config.locationId() == null ? 0
                    : CloudRowMirrorService.synchronize(conn, config.locationId()).uploaded();
            System.out.println("Image reconciliation complete: references=" + result.references()
                    + ", uploaded=" + result.uploaded() + ", repaired=" + result.repaired()
                    + ", metadataRows=" + metadataRows
                    + ", pending=" + counts.pendingUploads() + ", missingLocal=" + counts.missingLocal()
                    + ", missingCloud=" + counts.missingCloud() + ", unused=" + counts.unused()
                    + ", failedPurges=" + counts.failedPurges());
        }
    }
}
