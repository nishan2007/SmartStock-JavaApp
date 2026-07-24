package app;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.CloudRowMirrorService;
import services.CloudSyncApi;
import services.CloudSyncManifest;
import services.ServerSupabaseCredentials;
import services.SupabaseProjectConfig;
import services.SyncSchemaInstaller;

import java.sql.Connection;

/**
 * Headless end-to-end check for the development server's API-only cloud path.
 *
 * <p>This command deliberately refuses to run against production. It prints
 * only aggregate counts and never prints credentials or business row data.</p>
 */
public final class DevelopmentCloudSyncSmokeMain {
    private static final int MAX_DELTA_PAGES = 100;

    private DevelopmentCloudSyncSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        DatabaseConfig database = DatabaseConfig.load();
        SupabaseProjectConfig project = SupabaseProjectConfig.load();
        requireDevelopmentServer(database, project);
        if (!ServerSupabaseCredentials.isConfigured()) {
            throw new IllegalStateException(
                    "Save the development Supabase Server Key in Database Setup before running this check.");
        }

        int locationId = database.locationId();
        CloudSyncManifest cloudSchema = CloudSyncManifest.fetch();
        if (!cloudSchema.hasTable("sync_outbox")) {
            throw new IllegalStateException(
                    "The development cloud schema is missing the SmartStock sync tables.");
        }

        try (Connection local = DB.getConnection()) {
            SyncSchemaInstaller.ensureSchema(local);
            CloudRowMirrorService.MirrorResult mirror =
                    CloudRowMirrorService.synchronize(local, locationId);

            int acknowledged = 0;
            int downloaded = 0;
            long cursor = -1;
            int pages = 0;
            boolean hasMore;
            do {
                CloudSyncApi.ExchangeResult exchange =
                        CloudSyncApi.exchange(local, locationId);
                if (exchange.hasMore() && exchange.nextCursor() <= cursor) {
                    throw new IllegalStateException(
                            "Development cloud delta cursor did not advance.");
                }
                cursor = exchange.nextCursor();
                acknowledged += exchange.acknowledged();
                downloaded += exchange.downloaded();
                hasMore = exchange.hasMore();
                pages++;
                if (pages > MAX_DELTA_PAGES) {
                    throw new IllegalStateException(
                            "Development cloud delta backlog exceeded the smoke-test limit.");
                }
            } while (hasMore);

            CloudSyncManifest storeMirror =
                    CloudSyncManifest.fetchStoreSnapshot(locationId);
            long materializedRows = storeMirror.tables().values().stream()
                    .mapToLong(CloudSyncManifest.TableInfo::rowCount)
                    .sum();
            if (materializedRows != mirror.activeRows()) {
                throw new IllegalStateException(
                        "Development cloud mirror row count does not match the local snapshot.");
            }

            System.out.printf(
                    "DEVELOPMENT CLOUD SYNC PASSED project=%s location=%d "
                            + "tables=%d activeRows=%d uploaded=%d unchanged=%d "
                            + "deleted=%d acknowledged=%d downloaded=%d cursor=%d%n",
                    project.projectRef(), locationId, storeMirror.tables().size(),
                    materializedRows, mirror.uploaded(), mirror.unchanged(),
                    mirror.deleted(), acknowledged, downloaded, cursor);
        }
    }

    static void requireDevelopmentServer(DatabaseConfig database,
                                         SupabaseProjectConfig project) {
        if (project.isProduction()
                || !SupabaseProjectConfig.DEVELOPMENT_PROJECT_REF.equals(project.projectRef())) {
            throw new IllegalStateException(
                    "This smoke test can run only against the SmartStock development Supabase project.");
        }
        if (database.mode() != DatabaseMode.SERVER) {
            throw new IllegalStateException(
                    "This smoke test requires SmartStock SERVER mode.");
        }
        if (database.locationId() == null || database.locationId() <= 0) {
            throw new IllegalStateException(
                    "This smoke test requires a configured Store Location ID.");
        }
        if (!database.hasPrimaryConnection()
                || database.hasUnresolvedCredentialPlaceholders()) {
            throw new IllegalStateException(database.missingPrimaryConnectionMessage());
        }
    }
}
