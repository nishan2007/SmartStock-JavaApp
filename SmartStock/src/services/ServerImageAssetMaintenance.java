package services;

import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;

import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;

/** Headless, server-only image registry reconciliation for provisioning and operations. */
public final class ServerImageAssetMaintenance {
    private ServerImageAssetMaintenance() { }

    public static void main(String[] args) throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER) {
            throw new IllegalStateException("Image maintenance can run only on the SmartStock server.");
        }
        String command=args.length==0?"sync":args[0].trim().toLowerCase();
        if("configure".equals(command)){
            if(args.length!=6)throw new IllegalArgumentException("Usage: configure <tenant-id> <client-id> <user-id-or-upn> <certificate-pem> <private-key-pem>");
            OneDriveImageStorageConfig.save(args[1],args[2],args[3],Files.readString(Path.of(args[4])),Files.readString(Path.of(args[5])));
            System.out.println("OneDrive image credentials saved in "+utils.SecureCredentialStore.backendLabel()+".");return;
        }
        try (Connection conn = DB.getConnection()) {
            ServerImageAssetService.ensureSchema(conn);
            if("cleanup-supabase".equals(command)){
                if(args.length!=3||!"I_HAVE_VERIFIED_ALL_STORES".equals(args[2]))throw new IllegalArgumentException("Usage: cleanup-supabase <asset-uuid> I_HAVE_VERIFIED_ALL_STORES");
                ServerImageAssetService.purgeSupabaseRollbackCopy(conn,java.util.UUID.fromString(args[1]));
                System.out.println("Verified Supabase rollback copy removed for "+args[1]+".");return;
            }
            if("probe".equals(command)){System.out.println(ServerImageAssetService.probeOneDrive(conn).message());return;}
            if("begin".equals(command))ServerImageAssetService.beginOneDriveMigration(conn);
            if("activate".equals(command)){ServerImageAssetService.activateOneDrive(conn);System.out.println("OneDrive product image storage is active.");return;}
            if("rollback".equals(command)){ServerImageAssetService.rollbackToSupabase(conn);System.out.println("Product image storage rolled back to Supabase.");return;}
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
