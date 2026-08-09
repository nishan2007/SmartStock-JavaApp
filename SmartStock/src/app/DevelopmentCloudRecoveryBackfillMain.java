package app;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.CloudRowMirrorService;
import services.CloudSyncManifest;
import services.ServerSupabaseCredentials;
import services.SupabaseProjectConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * One-time development-only backfill for verified recovery generations.
 * It never changes the configured store assignment and cannot target production.
 */
public final class DevelopmentCloudRecoveryBackfillMain {
    private DevelopmentCloudRecoveryBackfillMain() {
    }

    public static void main(String[] args) throws Exception {
        DatabaseConfig database = DatabaseConfig.load();
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        if (project.environment() != SupabaseProjectConfig.Environment.DEVELOPMENT
                || database.mode() != DatabaseMode.SERVER) {
            throw new IllegalStateException(
                    "Recovery backfill is restricted to a development store server.");
        }
        if (!ServerSupabaseCredentials.isConfigured()) {
            throw new IllegalStateException("The development Supabase Server Key is required.");
        }
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Pass one or more explicit integer store location IDs.");
        }

        try (Connection local = DB.getConnection()) {
            for (String argument : args) {
                int locationId = parseLocation(argument);
                requireLocalLocation(local, locationId);
                CloudRowMirrorService.MirrorResult result =
                        CloudRowMirrorService.synchronize(local, locationId);
                CloudSyncManifest manifest = CloudSyncManifest.fetchStoreSnapshot(locationId);
                if (!manifest.hasVerifiedSnapshot()
                        || manifest.totalRowCount() != result.activeRows()) {
                    throw new IllegalStateException(
                            "Verified recovery generation does not match location " + locationId + ".");
                }
                System.out.printf(
                        "DEVELOPMENT RECOVERY BACKFILL PASSED location=%d generation=%s "
                                + "tables=%d activeRows=%d uploaded=%d deleted=%d%n",
                        locationId, manifest.snapshotGenerationId(), manifest.tables().size(),
                        manifest.totalRowCount(), result.uploaded(), result.deleted());
            }
        }
    }

    private static int parseLocation(String argument) {
        try {
            int value = Integer.parseInt(argument);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Store location IDs must be positive integers.", ex);
        }
    }

    private static void requireLocalLocation(Connection local, int locationId) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
                "SELECT 1 FROM locations WHERE location_id=?")) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException(
                            "Store location " + locationId + " does not exist locally.");
                }
            }
        }
    }
}
