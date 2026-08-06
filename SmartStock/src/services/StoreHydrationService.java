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

    public static VerifiedHydrationResult restoreVerifiedReplacement(Connection local,Integer locationId)throws SQLException{
        if(locationId==null)return VerifiedHydrationResult.skipped("No store selected.");
        try{
            CloudSyncManifest mirror=CloudSyncManifest.fetchStoreSnapshot(locationId);
            int rows=CloudRecoveryService.restoreStoreMirror(local,locationId,mirror);
            ProductionRecoveryDrillService.verifyRestoredStore(local,mirror);
            ImageCacheWarmupService.warmLocalCache(local);
            return VerifiedHydrationResult.synced(rows,mirror.totalRowCount());
        }catch(java.io.IOException ex){throw new SQLException("The Supabase store mirror is unavailable.",ex);}
    }

    private static HydrationResult hydrate(Connection local, Integer locationId, boolean includeHistory) throws SQLException {
        DatabaseConfig config = DatabaseConfig.load();
        if (!ServerSupabaseCredentials.isConfigured()) {
            return HydrationResult.skipped("Supabase server API is not configured.");
        }
        Integer storeId = locationId == null ? config.locationId() : locationId;
        if (storeId == null) {
            return HydrationResult.skipped("No store is selected for cloud hydration.");
        }
        try {
            CloudSyncManifest mirror = CloudSyncManifest.fetchStoreSnapshot(storeId);
            int rows = CloudRecoveryService.restoreStoreMirror(local, storeId, mirror);
            ImageCacheWarmupService.warmLocalCache(local);
            return HydrationResult.synced(rows);
        } catch (java.io.IOException ex) {
            throw new SQLException("The Supabase store mirror is unavailable.", ex);
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

    public record VerifiedHydrationResult(boolean attempted,int rows,String message,long activeRowCount) {
        static VerifiedHydrationResult synced(int rows,long activeRowCount) {
            return new VerifiedHydrationResult(true,rows,"Pulled "+rows+" cloud row(s).",activeRowCount);
        }
        static VerifiedHydrationResult skipped(String message) {
            return new VerifiedHydrationResult(false,0,message,0);
        }
    }
}
