package services;

import data.DatabaseConfig;
import data.DatabaseMode;

import java.util.Set;

/** Shared deny-by-default policy for operations that require physical store presence. */
public final class RemoteAdminPolicy {
    private static final Set<String> BLOCKED_PREFIXES = Set.of(
            "/v1/cash/", "/v1/cash-drawers/", "/v1/held-carts/",
            "/v1/time-clock/punch", "/v1/employees/badge-",
            "/v1/workstation/", "/v1/sync/run", "/v1/sync/resolve"
    );
    private static final Set<String> BLOCKED_EXACT = Set.of(
            "/v1/sales/checkout", "/v1/sales/refund", "/v1/inventory/receive",
            "/v1/transfers/receive", "/v1/cloud/storage/upload"
    );
    private static final Set<String> OFFLINE_SAFE_PREFIXES = Set.of(
            "/v1/configuration/", "/v1/employees/admin/", "/v1/schedule/",
            "/v1/security/roles/", "/v1/catalog/departments/", "/v1/catalog/vendors/",
            "/v1/catalog/customer-types/", "/v1/maintenance/"
    );

    private RemoteAdminPolicy() { }

    public static boolean isRemoteAdminClient() {
        return DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN;
    }

    public static boolean isPhysicalOperation(String path) {
        if (path == null) return false;
        if (BLOCKED_EXACT.contains(path)) return true;
        return BLOCKED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    public static void requireClientOperationAllowed(String path) {
        if (isRemoteAdminClient() && isPhysicalOperation(path)) {
            throw new IllegalStateException("This action requires physical access to the selected store and is unavailable in Remote Admin mode.");
        }
    }

    public static boolean isMutation(String path) {
        if (path == null) return false;
        return path.endsWith("/update") || path.endsWith("/save") || path.endsWith("/create")
                || path.endsWith("/assign") || path.endsWith("/unassign") || path.endsWith("/adjust")
                || path.endsWith("/add") || path.endsWith("/delete") || path.endsWith("/deactivate")
                || path.endsWith("/apply") || path.endsWith("/pay") || path.endsWith("/bonus")
                || path.contains("/mutation");
    }

    public static boolean isOfflineSafeMutation(String path) {
        return isMutation(path) && OFFLINE_SAFE_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
