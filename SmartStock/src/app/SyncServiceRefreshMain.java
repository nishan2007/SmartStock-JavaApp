package app;

import services.PostgresRuntimeService;

/** Explicit maintenance entry point for refreshing the installed background service. */
public final class SyncServiceRefreshMain {
    private SyncServiceRefreshMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !"--confirm-refresh".equals(args[0])) {
            throw new IllegalArgumentException(
                    "Usage: SyncServiceRefreshMain --confirm-refresh");
        }
        PostgresRuntimeService.CommandResult result =
                PostgresRuntimeService.refreshSyncServiceInstallation();
        if (!result.success()) {
            throw new IllegalStateException(result.output());
        }
        System.out.println(result.output());
    }
}
